package com.mas.masServer.entity;

public enum AuthenticationSessionStatus {
    // CREATED,
    ACTIVE,                 // challenge in progress
    COMPLETED,              // valid authorization
    ARCHIVED_COMPLETED,     // historical, NOT valid
    CANCELLED,
    EXPIRED
}
