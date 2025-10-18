package com.mas.masServer.repository;


import com.mas.masServer.entity.Document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByMembership_Group_GroupId(Long groupId);
}
