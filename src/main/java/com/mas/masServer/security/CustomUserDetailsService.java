package com.mas.masServer.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.User; 
import com.mas.masServer.repository.UserRepository; 
import com.mas.masServer.repository.MembershipRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Override
    public UserDetails loadUserByUsername(String emailId) throws UsernameNotFoundException {
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + emailId));

        // System role (e.g., ROLE_ADMIN, ROLE_USER)
        String systemRole = user.getRole() != null ? user.getRole().getRoleName() : "USER";
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + systemRole.toUpperCase()));

        // Group roles from memberships
        List<Membership> memInfo = membershipRepository.findByUser(user);
        for (Membership membership : memInfo) {
            String groupRole = membership.getGroupRole().getRoleName();
            Long groupId = membership.getGroup().getGroupId();
            // Format: GROUP_ROLE_<groupId>_<roleName> (e.g., GROUP_ROLE_1_GROUP_MANAGER)
            authorities.add(new SimpleGrantedAuthority("GROUP_ROLE_" + groupId + "_" + groupRole.toUpperCase()));
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmailId())
                .password(user.getPassword()) // Assumes encoded
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.getIsEmailVerified())
                .build();
    }
}