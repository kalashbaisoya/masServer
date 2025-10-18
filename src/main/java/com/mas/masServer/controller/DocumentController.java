package com.mas.masServer.controller;

import com.mas.masServer.dto.DocumentDownloadResponse;
import com.mas.masServer.dto.DocumentResponseDto;
import com.mas.masServer.dto.DocumentUploadRequest;
import com.mas.masServer.dto.DocumentUploadResponse;
import com.mas.masServer.service.DocumentService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") DocumentUploadRequest request) {
        return ResponseEntity.ok(documentService.uploadDocument(request, file));
    }

    @GetMapping("/download/{documentId}")
    public ResponseEntity<DocumentDownloadResponse> downloadDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.downloadDocument(documentId));
    }

    /**
     * View all documents in a group by group id
     * Valid For Authenticated Group Members with role GM, MEMBER and PANELIST
     */

    @GetMapping("/groups/{groupId}/view-all")
    public ResponseEntity<List<DocumentResponseDto>> viewAllDocumentsByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(documentService.viewAllDocumentsByGroupId(groupId));
    }
    

}