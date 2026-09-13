package dev.onurerkoc.payguard.entity;

public enum UserRole {

    // Yalnızca kendi müşteri bilgileri ve kartlarıyla işlem yapabilir.
    USER,

    // Açıkça izin verilen yönetim işlemlerini yapabilir.
    ADMIN
}