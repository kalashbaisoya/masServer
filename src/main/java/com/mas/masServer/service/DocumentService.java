package com.mas.masServer.service;


import com.mas.masServer.dto.DocumentDownloadResponse;
import com.mas.masServer.dto.DocumentUploadRequest;
import com.mas.masServer.dto.DocumentUploadResponse;
import com.mas.masServer.entity.Document;
import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.IsOnline;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.MembershipStatus;
import com.mas.masServer.entity.User;
import com.mas.masServer.repository.DocumentRepository;
import com.mas.masServer.repository.GroupAuthStateRepository;
import com.mas.masServer.repository.GroupRepository;
import com.mas.masServer.repository.MembershipRepository;
import com.mas.masServer.repository.UserRepository;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupAuthStateRepository groupAuthStateRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private AuditLogService auditLogService; // For logging

    @Value("${minio.bucket-name}")
    private String bucketName;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] ALLOWED_FILE_TYPES = {"application/pdf", "image/png", "image/jpeg"};

    @Transactional
    public DocumentUploadResponse uploadDocument(DocumentUploadRequest request, MultipartFile file) {
        // Get authenticated user
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate group
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Validate membership
        Membership membership = membershipRepository.findByUserAndGroup(user, group);
        if (membership == null || !MembershipStatus.ACTIVE.equals(membership.getStatus())) {
            auditLogService.log(user.getUserId(), "document", "access_attempt", null, "Denied: Not active member", "Access denied");
            throw new RuntimeException("User is not an active member of this group");
        }

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB");
        }
        if (!Arrays.asList(ALLOWED_FILE_TYPES).contains(file.getContentType())) {
            throw new RuntimeException("Invalid file type. Allowed types: PDF, PNG, JPEG");
        }

        // Generate unique file ID
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName != null ? originalFileName.substring(originalFileName.lastIndexOf(".")) : "";
        String fileId = UUID.randomUUID() + fileExtension;

        // Upload to MinIO
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileId)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }

        // Save document metadata
        Document document = new Document();
        document.setGroup(group);
        document.setMembership(membership);
        document.setFileName(fileId);
        document.setFileType(file.getContentType());
        document.setAccessType(request.getAccessType());
        document.setFileId(fileId);
        document = documentRepository.save(document);

        // Log to AuditLog
        auditLogService.log(
                user.getUserId(),
                "document",
                "fileId",
                null,
                fileId,
                "Document uploaded"
        );

        // Prepare response
        DocumentUploadResponse response = new DocumentUploadResponse();
        response.setDocumentId(document.getDocumentId());
        response.setFileName(fileId);
        response.setFileType(document.getFileType());
        response.setAccessType(document.getAccessType());
        response.setFileId(fileId);
        response.setMessage("Document uploaded successfully");

        return response;
    }


    @Transactional(readOnly = true)
    public DocumentDownloadResponse downloadDocument(Long documentId) {
        // Get authenticated user
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Find document
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Group group = document.getGroup();

        // Verify user is an active member of the group
        Membership membership = membershipRepository.findByUserAndGroup(user, group);
        if (membership == null || !MembershipStatus.ACTIVE.equals(membership.getStatus())) {
            auditLogService.log(user.getUserId(), "document", "access_attempt", null, "Denied: Not active member", "Access denied");
            throw new RuntimeException("User is not an active member of this group");
        }

        // Check group access policy
        if (!isAccessAllowed(group)) {
            auditLogService.log(user.getUserId(), "document", "access_attempt", null, "Denied: Quorum not met", "Access denied");
            throw new RuntimeException("Access denied: Group quorum requirements not met");
        }

        // Generate presigned URL (expires in 10 minutes)
        String fileId = document.getFileId();
        String presignedUrl;
        try {
            presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(fileId)
                            .expiry(10, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }

        // Log successful access
        auditLogService.log(user.getUserId(), "document", "access_granted", null, fileId, "Document accessed");

        DocumentDownloadResponse response = new DocumentDownloadResponse();
        response.setDownloadUrl(presignedUrl);
        response.setMessage("Access granted. Use the URL to download the document.");
        return response;
    }

    private boolean isAccessAllowed(Group group) {
        switch (group.getGroupAuthType()) {
            case A:
                return true; // No quorum required

            case B:
                // Require all MEMBER + GM online
                long totalMembers = membershipRepository.countByGroupAndGroupRoleRoleName(group, "MEMBER");
                List<GroupAuthState> onlineStates = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

                long onlineMembers = onlineStates.stream()
                        .filter(state -> "MEMBER".equals(state.getMembership().getGroupRole().getRoleName()))
                        .count();

                // Check GM online
                Membership gmMembership = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
                boolean gmOnline = groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembership.getMembershipId(), IsOnline.Y) != null;

                return onlineMembers == totalMembers && gmOnline;

            case C:
                // Require all PANELIST + GM online
                long totalPanelists = membershipRepository.countByGroupAndGroupRoleRoleName(group, "PANELIST");
                onlineStates = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

                long onlinePanelists = onlineStates.stream()
                        .filter(state -> "PANELIST".equals(state.getMembership().getGroupRole().getRoleName()))
                        .count();

                // Check GM online
                gmMembership = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
                gmOnline = groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembership.getMembershipId(), IsOnline.Y) != null;

                return onlinePanelists == totalPanelists && gmOnline;

            case D:
                // Require at least quorumK online (any role)
                long onlineCount = groupAuthStateRepository.countByMembershipGroupAndIsOnline(group, IsOnline.Y);
                return onlineCount >= group.getQuorumK();

            default:
                return false;
        }
    }
}