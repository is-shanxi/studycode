package com.certpractice.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByCode(String code);

    List<Certificate> findByStatusOrderBySortOrderAsc(CatalogStatus status);
}
