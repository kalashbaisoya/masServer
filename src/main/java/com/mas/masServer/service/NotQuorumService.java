



// package com.mas.masServer.service;

// import java.security.SecureRandom;
// import java.time.LocalDateTime;
// import java.util.*;
// import java.util.stream.Collectors;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.messaging.simp.SimpMessagingTemplate;
// import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.security.core.context.SecurityContextHolder;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import com.mas.masServer.dto.CreateChallengeResponse;
// import com.mas.masServer.entity.Group;
// import com.mas.masServer.entity.GroupAuthState;
// import com.mas.masServer.entity.GroupChallengeState;
// import com.mas.masServer.entity.IsOnline;
// import com.mas.masServer.entity.Membership;
// import com.mas.masServer.entity.MembershipStatus;
// import com.mas.masServer.entity.QuorumChallenge;
// import com.mas.masServer.entity.QuorumAuthStatus;
// import com.mas.masServer.entity.User;
// import com.mas.masServer.repository.GroupAuthStateRepository;
// import com.mas.masServer.repository.GroupChallengeStateRepository;
// import com.mas.masServer.repository.GroupRepository;
// import com.mas.masServer.repository.MembershipRepository;
// import com.mas.masServer.repository.QuorumChallengeRepository;
// import com.mas.masServer.repository.UserRepository;

// @Service
// @Transactional
// public class QuorumService {

//     private static final Logger log = LoggerFactory.getLogger(QuorumService.class);

//     private static final SecureRandom secureRandom = new SecureRandom();

//     @Autowired private QuorumChallengeRepository challengeRepo;
//     @Autowired private MembershipRepository membershipRepository;
//     @Autowired private EmailService emailService;
//     @Autowired private GroupRepository groupRepository;
//     @Autowired private UserRepository userRepository;
//     @Autowired private SimpMessagingTemplate messagingTemplate;
//     @Autowired private GroupAuthStateRepository groupAuthStateRepository;
//     @Autowired private GroupChallengeStateRepository groupChallengeStateRepository;
//     // @Autowired private LoginService loginService;

//     /**
//      * Create or return an active challenge for a group.
//      * - If an unexpired challenge already exists, return it.
//      * - Otherwise create a new challenge according to group type and persist state.
//      */
//     @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
//     public CreateChallengeResponse createChallenge(Long groupId) {
//         Group group = groupRepository.findById(groupId)
//                 .orElseThrow(() -> new RuntimeException("Group not found"));

//         // check for existing GroupChallengeState
//         GroupChallengeState gs = groupChallengeStateRepository.findById(groupId).orElse(null);
//         if (gs != null && gs.getCurrentChallenge() != null) {
//             QuorumChallenge existing = gs.getCurrentChallenge();
//             if (!existing.isExpired()) {
//                 // return existing active challenge (don't generate new OTPs)
//                 log.info("Returning existing active challenge {} for group {}", existing.getChallengeId(), groupId);
//                 // refresh GroupChallengeState fields for UI broadcast
//                 broadcastGroupState(groupId, gs);
//                 return mapToChallengeResponseDto(existing);
//             } else {
//                 // mark expired in state and persist, allow new challenge creation
//                 gs.setState(QuorumAuthStatus.EXPIRED);
//                 gs.setLastUpdated(LocalDateTime.now());
//                 gs.setExpiresAt(null);
//                 gs.setCurrentChallenge(null);
//                 gs.setInitiator(null);
//                 groupChallengeStateRepository.save(gs);
//                 messagingTemplate.convertAndSend("/topic/group/" + groupId + "/gc-state", buildStatePayload(gs));
//             }
//         }

//         String userMail = SecurityContextHolder.getContext().getAuthentication().getName();
//         User user = userRepository.findByEmailId(userMail)
//                 .orElseThrow(() -> new RuntimeException("User not found"));

//         // Basic online precheck across types (some types need special checks)
//         if (!areAllMembersOnline(group)) {
//             throw new IllegalStateException("Required members are not currently online for this group auth type");
//         }

//         // Create challenge
//         QuorumChallenge challenge = new QuorumChallenge();
//         challenge.setGroup(group);
//         challenge.setInitiator(user);
//         // createdAt and expiresAt are set by the entity default (if not, set here)
//         challenge.setCreatedAt(LocalDateTime.now());
//         challenge.setExpiresAt(LocalDateTime.now().plusMinutes(5));

//         // Populate OTPs according to group type
//         switch (group.getGroupAuthType()) {
//             case A:
//                 // Only the initiator gets an OTP — Type A
//                 challenge.getMemberOtpMap().put(userMail, generateOtp());
//                 log.info("Generated OTP for initiator {} in group {}", userMail, group.getGroupId());
//                 break;

//             case B:
//                 // Everyone with active membership gets OTP
//                 List<Membership> allMembersB = membershipRepository.findByGroupAndStatusIn(
//                         group, List.of(MembershipStatus.ACTIVE));
//                 for (Membership m : allMembersB) {
//                     String email = m.getUser().getEmailId();
//                     challenge.getMemberOtpMap().put(email, generateOtp());
//                     log.info("Generated OTP for {} in group {}", email, group.getGroupId());
//                 }
//                 break;

//             case C:
//                 // Only GM + PANELIST roles
//                 List<Membership> gmAndPanelists = membershipRepository.findByGroupAndStatusIn(
//                         group, List.of(MembershipStatus.ACTIVE)).stream()
//                         .filter(m -> {
//                             String role = m.getGroupRole().getRoleName();
//                             return "GROUP_MANAGER".equals(role) || "PANELIST".equals(role);
//                         })
//                         .collect(Collectors.toList());

//                 for (Membership m : gmAndPanelists) {
//                     String email = m.getUser().getEmailId();
//                     challenge.getMemberOtpMap().put(email, generateOtp());
//                     log.info("Generated OTP for {} in group {}", email, group.getGroupId());
//                 }
//                 break;

//             case D:
//                 // Online members where count >= quorumK
//                 List<GroupAuthState> onlineStates = groupAuthStateRepository
//                         .findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

//                 if (onlineStates.size() < group.getQuorumK()) {
//                     log.warn("Not enough online members to create challenge for group {}", group.getGroupId());
//                     throw new IllegalStateException("Not enough online members to meet quorum");
//                 }

//                 // select online members (any role)
//                 for (GroupAuthState state : onlineStates) {
//                     String email = state.getMembership().getUser().getEmailId();
//                     challenge.getMemberOtpMap().put(email, generateOtp());
//                     log.info("Generated OTP for online member {} in group {}", email, group.getGroupId());
//                 }
//                 break;

//             default:
//                 log.warn("Unsupported group auth type {} for group {}", group.getGroupAuthType(), group.getGroupId());
//                 throw new IllegalStateException("Unsupported group type");
//         }

//         // Persist challenge
//         QuorumChallenge saved = challengeRepo.save(challenge);
//         log.info("Quorum challenge created: {} for group {}", saved.getChallengeId(), group.getGroupId());

//         // Persist/update GroupChallengeState
//         GroupChallengeState state = groupChallengeStateRepository.findById(groupId).orElse(null);
//         if (state == null) {
//             state = new GroupChallengeState();
//             state.setGroup(group); // MapsId will use group's id when saving
//         }

//         state.setCurrentChallenge(saved);
//         state.setInitiator(user);
//         state.setExpiresAt(saved.getExpiresAt());
//         state.setState(QuorumAuthStatus.WAITING_FOR_OTP);
//         state.setLastUpdated(LocalDateTime.now());
//         groupChallengeStateRepository.save(state);

//         // Send OTPs and broadcast state
//         sendQuorumChallengeEmails(groupId, saved);
//         broadcastGroupState(groupId, state);

//         return mapToChallengeResponseDto(saved);
//     }

//     /**
//      * Verifies otp for the current user in the specified group.
//      * Updates challenge and group state accordingly and broadcasts updates.
//      */
//     public boolean verifyQuorumOtp(Long groupId, String otp) {
//         String email = SecurityContextHolder.getContext().getAuthentication().getName();
//         GroupChallengeState gs = groupChallengeStateRepository.findById(groupId).orElse(null);
//         if (gs == null || gs.getCurrentChallenge() == null) {
//             throw new IllegalArgumentException("No active challenge for group: " + groupId);
//         }

//         QuorumChallenge challenge = gs.getCurrentChallenge();

//         // If challenge expired => move state to EXPIRED and broadcast
//         if (challenge.isExpired() || (gs.getExpiresAt() != null && LocalDateTime.now().isAfter(gs.getExpiresAt()))) {
//             gs.setState(QuorumAuthStatus.EXPIRED);
//             gs.setCurrentChallenge(null);
//             gs.setInitiator(null);
//             gs.setExpiresAt(null);
//             gs.setLastUpdated(LocalDateTime.now());
//             groupChallengeStateRepository.save(gs);
//             broadcastGroupState(groupId, gs);
//             throw new IllegalStateException("Challenge has expired");
//         }

//         String expectedOtp = challenge.getMemberOtpMap().get(email);
//         if (expectedOtp != null && expectedOtp.equals(otp)) {
//             // mark as verified
//             boolean added = challenge.getVerifiedMembers().add(email);
//             if (added) {
//                 challengeRepo.save(challenge);
//                 log.info("OTP verified for {} in group {}", email, groupId);
//             } else {
//                 log.debug("OTP already verified for {} in group {}", email, groupId);
//             }

//             // Evaluate completion depending on group type
//             if (isChallengeComplete(challenge)) {
//                 challenge.setAllVerified(true);
//                 challengeRepo.save(challenge);

//                 // update group state to FULLY_VERIFIED
//                 gs.setState(QuorumAuthStatus.FULLY_VERIFIED);
//                 gs.setLastUpdated(LocalDateTime.now());
//                 gs.setExpiresAt(null); // no expiry once done
//                 groupChallengeStateRepository.save(gs);

//                 // broadcast and notify
//                 broadcastGroupState(groupId, gs);
//                 messagingTemplate.convertAndSend("/topic/group/" + groupId + "/quorum-approved", true);
//                 log.info("QUORUM CHALLENGE FULLY APPROVED for group {}", challenge.getGroup().getGroupId());
//             } else {
//                 // partial verification
//                 gs.setState(QuorumAuthStatus.PARTIALLY_VERIFIED);
//                 gs.setLastUpdated(LocalDateTime.now());
//                 groupChallengeStateRepository.save(gs);
//                 broadcastGroupState(groupId, gs);
//             }

//             return true;
//         } 
        
//         // else if (expectedOtp == null) {
//         //     // No expected OTP for this user -> maybe they provided a session token to validate
//         //     boolean valid = loginService.isValidUser(otp);
//         //     if (valid) {
//         //         log.info("Login service validation passed for token by {} in group {}", email, groupId);
//         //         // This branch depends on your business rules whether to treat as verified or not.
//         //         // Here we do not mark them verified in challenge; just return the result.
//         //         return true;
//         //     }
//         // }

//         return false;
//     }

//     public boolean isChallengeFullyVerified(String challengeId) {
//         return challengeRepo.findByChallengeId(challengeId)
//                 .map(c -> c.isAllVerified() && !c.isExpired())
//                 .orElse(false);
//     }

//     private String generateOtp() {
//         int number = secureRandom.nextInt(1_000_000);
//         return String.format("%06d", number);
//     }

//     private void sendQuorumChallengeEmails(Long groupId, QuorumChallenge saved) {
//         saved.getMemberOtpMap().forEach((email, otp) -> {
//             String subject = "OTP for MAS app Quorum";
//             String message = "Your OTP is: " + otp + ". It is valid until " + saved.getExpiresAt() + ". Do not share it with anyone. \n Initiated By: " +
//                     (saved.getInitiator() != null ? saved.getInitiator().getEmailId() : "System");
//             try {
//                 emailService.sendNotification(email, subject, message);
//                 log.info("Sent OTP to {} for group {}", email, groupId);
//             } catch (Exception e) {
//                 log.error("Failed to send OTP to {} for group {}: {}", email, groupId, e.getMessage(), e);
//             }
//         });
//     }

//     /**
//      * Check if challenge is complete according to group rules.
//      */
//     private boolean isChallengeComplete(QuorumChallenge challenge) {
//         Group group = challenge.getGroup();
//         switch (group.getGroupAuthType()) {
//             case A:
//                 // any single verified (must be initiator ideally)
//                 return challenge.getVerifiedMembers().size() >= 1;

//             case B:
//                 // all active members must verify
//                 List<Membership> membersB = membershipRepository.findByGroupAndStatusIn(group, List.of(MembershipStatus.ACTIVE));
//                 long requiredB = membersB.size();
//                 return challenge.getVerifiedMembers().size() >= requiredB;

//             case C:
//                 // GM + PANELIST must verify
//                 List<Membership> gmAndPanelists = membershipRepository.findByGroupAndStatusIn(group, List.of(MembershipStatus.ACTIVE))
//                         .stream()
//                         .filter(m -> {
//                             String role = m.getGroupRole().getRoleName();
//                             return "GROUP_MANAGER".equals(role) || "PANELIST".equals(role);
//                         })
//                         .collect(Collectors.toList());
//                 long requiredC = gmAndPanelists.size();
//                 return challenge.getVerifiedMembers().containsAll(
//                         gmAndPanelists.stream().map(m -> m.getUser().getEmailId()).collect(Collectors.toSet())
//                 ) && challenge.getVerifiedMembers().size() >= requiredC;

//             case D:
//                 // need at least quorumK verified among OTP recipients
//                 int k = group.getQuorumK() != null ? group.getQuorumK() : 0;
//                 return challenge.getVerifiedMembers().size() >= k;

//             default:
//                 return false;
//         }
//     }

//     /**
//      * Broadcast GroupChallengeState to subscribed clients.
//      * Payload includes state, challengeId, initiatorEmail, expiresAt, verifiedCount and requiredCount (when available)
//      */
//     private void broadcastGroupState(Long groupId, GroupChallengeState gs) {
//         Map<String, Object> payload = buildStatePayload(gs);
//         messagingTemplate.convertAndSend("/topic/group/" + groupId + "/gc-state", payload);
//     }

//     private Map<String, Object> buildStatePayload(GroupChallengeState gs) {
//         Map<String, Object> payload = new HashMap<>();
//         payload.put("state", gs.getState().name());
//         payload.put("lastUpdated", gs.getLastUpdated());
//         payload.put("expiresAt", gs.getExpiresAt());
//         if (gs.getInitiator() != null) payload.put("initiatorEmail", gs.getInitiator().getEmailId());
//         if (gs.getCurrentChallenge() != null) {
//             QuorumChallenge ch = gs.getCurrentChallenge();
//             payload.put("challengeId", ch.getChallengeId());
//             payload.put("verifiedCount", ch.getVerifiedMembers().size());
//             payload.put("requiredCount", ch.getMemberOtpMap().size());
//             // optionally include list of verified members (careful with size / privacy)
//             payload.put("verifiedMembers", new ArrayList<>(ch.getVerifiedMembers()));
//         } else {
//             payload.put("challengeId", null);
//             payload.put("verifiedCount", 0);
//             payload.put("requiredCount", 0);
//         }
//         return payload;
//     }

//     /**
//      * Checks whether required members are online for each group type.
//      * Note: preserves your existing GroupAuthState concept for online tracking.
//      */
//     private boolean areAllMembersOnline(Group group) {
//         switch (group.getGroupAuthType()) {
//             case A:
//                 return true; // No quorum required for type A beyond initiator

//             case B:
//                 // require all ACTIVE members + GM to be online
//                 List<Membership> allMembers = membershipRepository.findByGroupAndStatusIn(group, List.of(MembershipStatus.ACTIVE));
//                 long totalMembers = allMembers.size()-1;

//                 List<GroupAuthState> onlineStatesB = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);
//                 long onlineMembers = onlineStatesB.stream()
//                         .filter(state -> "MEMBER".equals(state.getMembership().getGroupRole().getRoleName())
//                                 && state.getMembership().getStatus() != MembershipStatus.SUSPENDED)
//                         .count();

//                 Membership gmMembershipB = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
//                 boolean gmOnlineB = gmMembershipB != null && groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembershipB.getMembershipId(), IsOnline.Y) != null;
                
//                 // evaluate LHS (equality of counts) and RHS (gm online) and log before returning
//                 boolean lhsEqual = totalMembers == onlineMembers;
//                 boolean rhs = gmOnlineB;
//                 System.out.println("areAllMembersOnline - totalMembers: " + totalMembers
//                         + ", onlineMembers: " + onlineMembers
//                         + ", lhsEqual: " + lhsEqual
//                         + ", gmOnlineB: " + gmOnlineB);
//                 log.info("areAllMembersOnline - totalMembers={}, onlineMembers={}, lhsEqual={}, gmOnlineB={}",
//                         totalMembers, onlineMembers, lhsEqual, gmOnlineB);
//                 return lhsEqual && rhs;

//             case C:
//                 // require PANELISTs + GM online
//                 List<Membership> panelists = membershipRepository.findByGroupAndStatusIn(group, List.of(MembershipStatus.ACTIVE))
//                         .stream()
//                         .filter(m -> "PANELIST".equals(m.getGroupRole().getRoleName()))
//                         .collect(Collectors.toList());

//                 long totalPanelists = panelists.size();
//                 List<GroupAuthState> onlineStatesC = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);
//                 long onlinePanelists = onlineStatesC.stream()
//                         .filter(state -> "PANELIST".equals(state.getMembership().getGroupRole().getRoleName()))
//                         .count();

//                 Membership gmMembershipC = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
//                 boolean gmOnlineC = gmMembershipC != null && groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembershipC.getMembershipId(), IsOnline.Y) != null;

//                 return totalPanelists == onlinePanelists && gmOnlineC;

//             case D:
//                 // require at least quorumK online (any role)
//                 long onlineCount = groupAuthStateRepository.countByMembershipGroupAndIsOnline(group, IsOnline.Y);
//                 return onlineCount >= (group.getQuorumK() != null ? group.getQuorumK() : 0);

//             default:
//                 return false;
//         }
//     }

//     private CreateChallengeResponse mapToChallengeResponseDto(QuorumChallenge challenge) {
//         if (challenge == null) return null;

//         CreateChallengeResponse dto = new CreateChallengeResponse();
//         dto.setCreatedAt(challenge.getCreatedAt());
//         dto.setExpiresAt(challenge.getExpiresAt());
//         dto.setChallengeId(challenge.getChallengeId());
//         User initiator = challenge.getInitiator();
//         if (initiator != null) {
//             String email = initiator.getEmailId();
//             dto.setInitiatorEmail(email);
//             String initiatorName = java.util.stream.Stream.of(
//                     initiator.getFirstName(),
//                     initiator.getMiddleName(),
//                     initiator.getLastName())
//                     .filter(java.util.Objects::nonNull)
//                     .map(String::trim)
//                     .filter(s -> !s.isEmpty())
//                     .collect(java.util.stream.Collectors.joining(" "));
//             dto.setInitiatorName(initiatorName);
//         }
//         dto.setRequiredCount(challenge.getMemberOtpMap().size());
//         dto.setVerifiedCount(challenge.getVerifiedMembers().size());
//         return dto;
//     }

//     public boolean isChallengeFullyVerified(Long groupId){

//         GroupChallengeState gs = groupChallengeStateRepository.findById(groupId).orElse(null);
//         if (gs == null || gs.getCurrentChallenge() == null) {
//             return false;
//         }
//         return QuorumAuthStatus.FULLY_VERIFIED.equals(gs.getState());
//     }

// }




// //old version expired at 25 nov 2025, 8 in the morning

// // package com.mas.masServer.service;

// // import java.util.List;
// // import java.util.Random;
// // import java.util.stream.Collectors;

// // import org.slf4j.Logger;
// // import org.slf4j.LoggerFactory;
// // import org.springframework.beans.factory.annotation.Autowired;
// // import org.springframework.messaging.simp.SimpMessagingTemplate;
// // import org.springframework.security.access.prepost.PreAuthorize;
// // import org.springframework.security.core.context.SecurityContextHolder;
// // import org.springframework.stereotype.Service;
// // import org.springframework.transaction.annotation.Transactional;

// // import com.mas.masServer.dto.CreateChallengeResponse;
// // import com.mas.masServer.entity.Group;
// // import com.mas.masServer.entity.GroupAuthState;
// // import com.mas.masServer.entity.GroupChallengeState;
// // import com.mas.masServer.entity.IsOnline;
// // import com.mas.masServer.entity.Membership;
// // import com.mas.masServer.entity.MembershipStatus;
// // import com.mas.masServer.entity.QuorumChallenge;
// // import com.mas.masServer.entity.User;
// // import com.mas.masServer.repository.GroupAuthStateRepository;
// // import com.mas.masServer.repository.GroupChallengeStateRepository;
// // import com.mas.masServer.repository.GroupRepository;
// // import com.mas.masServer.repository.MembershipRepository;
// // import com.mas.masServer.repository.QuorumChallengeRepository;
// // import com.mas.masServer.repository.UserRepository;

// // @Service
// // @Transactional
// // public class QuorumService {

// //     private static final Logger log = LoggerFactory.getLogger(QuorumService.class);

// //     @Autowired private QuorumChallengeRepository challengeRepo;
// //     @Autowired private MembershipRepository membershipRepository;
// //     @Autowired private EmailService emailService;
// //     @Autowired private GroupRepository groupRepository;
// //     @Autowired private UserRepository userRepository;
// //     @Autowired private SimpMessagingTemplate messagingTemplate;
// //     @Autowired private GroupAuthStateRepository groupAuthStateRepository;
// //     @Autowired private GroupChallengeStateRepository groupChallengeStateRepository;
// //     @Autowired private LoginService loginService;

// //     @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
// //     public CreateChallengeResponse createChallenge(Long groupId) {
// //         Group group = groupRepository.findById(groupId)
// //                 .orElseThrow(() -> new RuntimeException("Group not found"));
// //         GroupChallengeState gs = groupChallengeStateRepository.findById(groupId).orElseGet(null);
// //                 if(gs !=null){
// //                     QuorumChallenge c = gs.getCurrentChallenge();
// //                     if(c != null && !c.isExpired()){
// //                         CreateChallengeResponse dto =mapToChallengeResponseDto(c);
// //                     return dto;
// //                     }
// //                 }

// //         String userMail = SecurityContextHolder.getContext().getAuthentication().getName();
// //         User user = userRepository.findByEmailId(userMail)
// //                 .orElseThrow(() -> new RuntimeException("User not found"));

// //         // if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
// //         //     throw new RuntimeException("UNAUTHORIZED: Only GM can initiate GROUP ACCESS");
// //         // }
// //         if (!areAllMembersOnline(group)) {
// //             throw new IllegalStateException("Not all members are currently online");
// //         }

// //         // finding memberships which were not deleted or suspended by the GM in past 
    
// //         // List<MembershipStatus> validStatuses = List.of(MembershipStatus.ACTIVE);
// //         // List<Membership> members = membershipRepository.findByGroupAndStatusIn(group, validStatuses);


// //     QuorumChallenge challenge = new QuorumChallenge();
// //     challenge.setGroup(group);
// //     challenge.setInitiator(user);

// //     switch (group.getGroupAuthType()) {
// //         case A:
// //             // Type A: challenge for a single membership only (initiator)
// //             String emailA = userMail;
// //             challenge.getMemberOtpMap().put(emailA, generateOtp());
// //             log.info("Generated OTP for initiator {} in group {}", emailA, group.getGroupId());
// //             break;

// //         case B:
// //             // Type B: challenge for all memberships (MEMBER + GM)
// //             List<Membership> allMembersB = membershipRepository.findByGroupAndStatusIn(
// //                     group, List.of(MembershipStatus.ACTIVE));
// //             for (Membership m : allMembersB) {
// //                 String email = m.getUser().getEmailId();
// //                 challenge.getMemberOtpMap().put(email, generateOtp());
// //                 log.info("Generated OTP for {} in group {}", email, group.getGroupId());
// //             }
// //             break;

// //         case C:
// //             // Type C: challenge for GM + PANELIST memberships only
// //             List<Membership> gmAndPanelists = membershipRepository.findByGroupAndStatusIn(
// //                     group, List.of(MembershipStatus.ACTIVE)).stream()
// //                     .filter(m -> {
// //                         String role = m.getGroupRole().getRoleName();
// //                         return "GROUP_MANAGER".equals(role) || "PANELIST".equals(role);
// //                     })
// //                     .collect(Collectors.toList());

// //             for (Membership m : gmAndPanelists) {
// //                 String email = m.getUser().getEmailId();
// //                 challenge.getMemberOtpMap().put(email, generateOtp());
// //                 log.info("Generated OTP for {} in group {}", email, group.getGroupId());
// //             }
// //             break;

// //         case D:
// //             // Type D: challenge only for online members, must meet quorumK
// //             List<GroupAuthState> onlineStates = groupAuthStateRepository
// //                     .findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

// //             if (onlineStates.size() < group.getQuorumK()) {
// //                 log.warn("Not enough online members to create challenge for group {}", group.getGroupId());
// //                 return null; // or throw exception depending on your business rules
// //             }

// //             for (GroupAuthState state : onlineStates) {
// //                 String email = state.getMembership().getUser().getEmailId();
// //                 challenge.getMemberOtpMap().put(email, generateOtp());
// //                 log.info("Generated OTP for online member {} in group {}", email, group.getGroupId());
// //             }
// //             break;

// //         default:
// //             log.warn("Unsupported group auth type {} for group {}", group.getGroupAuthType(), group.getGroupId());
// //             return null;
// //     }

// //     QuorumChallenge saved = challengeRepo.save(challenge);
// //     log.info("Quorum challenge created: {} for group {}", saved.getChallengeId(), group.getGroupId());


// //         GroupChallengeState gr = new GroupChallengeState();
// //         gr.setCurrentChallenge(challenge);
// //         groupChallengeStateRepository.save(gr);

// //         // Send OTPs via email
// //         sendQuorumChallengeEmails(groupId, saved);

// //         return mapToChallengeResponseDto(saved);
// //     }


// //     public boolean verifyQuorumOtp(Long groupId, String otp) {
// //         String email = SecurityContextHolder.getContext().getAuthentication().getName();
// //         GroupChallengeState gs = groupChallengeStateRepository.findById(groupId).orElseGet(null);
// //         QuorumChallenge challenge =null;
// //             if(gs !=null){
// //                 challenge = gs.getCurrentChallenge();
// //                 if(challenge == null){
// //                     throw new IllegalArgumentException("Invalid challenge");
// //                 }
// //             }

// //         if (challenge!=null) {
// //             if (challenge.isExpired()) {
// //                 throw new IllegalStateException("Challenge has expired");
// //             }

// //         String expectedOtp = challenge.getMemberOtpMap().get(email);
// //             if (expectedOtp != null && expectedOtp.equals(otp)) {
// //                 challenge.getVerifiedMembers().add(email);
// //                 challengeRepo.save(challenge);
// //                 log.info("OTP verified for {} in group {}", email, groupId);

// //                 if (challenge.getVerifiedMembers().size() == challenge.getMemberOtpMap().size()) {
// //                     challenge.setAllVerified(true);
// //                     challengeRepo.save(challenge);
// //                     log.info("QUORUM CHALLENGE FULLY APPROVED for group {}", challenge.getGroup().getGroupId());
// //                     messagingTemplate.convertAndSend("/topic/group/" + challenge.getGroup().getGroupId() + "/quorum-approved", true);
// //                 }
// //                 return true;
// //             }
// //             else if(expectedOtp==null) {
// //                 return loginService.isValidUser(otp);
// //             }
// //         }
        
// //         return false;
// //     }

// //     public boolean isChallengeFullyVerified(String challengeId) {
// //         return challengeRepo.findByChallengeId(challengeId)
// //             .map(c -> c.isAllVerified() && !c.isExpired())
// //             .orElse(false);
// //     }

// //     private String generateOtp() {
// //         return String.format("%06d", new Random().nextInt(999999));
// //     }

// //     private void sendQuorumChallengeEmails(Long groupId, QuorumChallenge saved) {
// //         saved.getMemberOtpMap().forEach((email, otp) -> {
// //             String subject = "OTP for MAS app Quorum";
// //             String message = "Your OTP is: " + otp + ". It is valid for 5 minutes. Do not share it with anyone. \n Initiated By: ";
// //             try {
// //                 emailService.sendNotification(email, subject, message);
// //                 log.info("Sent OTP to {} for group {}", email, groupId);
// //             } catch (Exception e) {
// //                 log.error("Failed to send OTP to {} for group {}: {}", email, groupId, e.getMessage(), e);
// //             }
// //         });
// //     }

// //     private boolean areAllMembersOnline(Group group) {
// //         switch (group.getGroupAuthType()) {
// //             case A:
// //                 return true; // No quorum required

// //             case B:
// //                 // Require all MEMBER + GM online
// //                 // long totalMembers = membershipRepository.countByGroupAndGroupRoleRoleName(group, "MEMBER");
// //                 long totalMembers = group.getQuorumK();
// //                 List<GroupAuthState> onlineStates = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

// //                 long onlineMembers = onlineStates.stream()
// //                         .filter(state -> "MEMBER".equals(state.getMembership().getGroupRole().getRoleName()))
// //                         .count();

// //                 // Check GM online
// //                 Membership gmMembership = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
// //                 boolean gmOnline = groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembership.getMembershipId(), IsOnline.Y) != null;

// //                 return onlineMembers == totalMembers && gmOnline;

// //             case C:
// //                 // Require all PANELIST + GM online
// //                 // long totalPanelists = membershipRepository.countByGroupAndGroupRoleRoleName(group, "PANELIST");
// //                 long totalPanelists = group.getQuorumK();

// //                 onlineStates = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

// //                 long onlinePanelists = onlineStates.stream()
// //                         .filter(state -> "PANELIST".equals(state.getMembership().getGroupRole().getRoleName()))
// //                         .count();

// //                 // Check GM online
// //                 gmMembership = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
// //                 gmOnline = groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembership.getMembershipId(), IsOnline.Y) != null;

// //                 return onlinePanelists == totalPanelists && gmOnline;

// //             case D:
// //                 // Require at least quorumK online (any role)
// //                 long onlineCount = groupAuthStateRepository.countByMembershipGroupAndIsOnline(group, IsOnline.Y);
// //                 return onlineCount >= group.getQuorumK();

// //             default:
// //                 return false;
// //             }
// //     }

// //     private CreateChallengeResponse mapToChallengeResponseDto(QuorumChallenge challenge) {
// //         if (challenge == null) return null;

// //         CreateChallengeResponse dto = new CreateChallengeResponse();
// //         dto.setCreatedAt(challenge.getCreatedAt());
// //         dto.setExpiresAt(challenge.getExpiresAt());
// //         // dto.setChallengeId(challenge.getChallengeId());
// //         User initiator =challenge.getInitiator();
// //         if (challenge.getInitiator() != null) {
// //             // Use initiator email as fallback for name if a separate name field isn't available
// //             String email = initiator.getEmailId();
// //             dto.setInitiatorEmail(email);
// //             String initiatorName = java.util.stream.Stream.of(
// //                 initiator.getFirstName(),
// //                 initiator.getMiddleName(),
// //                 initiator.getLastName())
// //                 .filter(java.util.Objects::nonNull)
// //                 .map(String::trim)
// //                 .filter(s -> !s.isEmpty())
// //                 .collect(java.util.stream.Collectors.joining(" "));
// //             dto.setInitiatorName(initiatorName);
// //         }

// //         return dto;
// //     }

// // }
