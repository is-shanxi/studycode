package com.certpractice.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findByCertificateIdOrderBySortOrderAsc(UUID certificateId);

    Optional<Subject> findByCertificateIdAndCode(UUID certificateId, String code);

    Optional<Subject> findFirstByCode(String code);

    List<Subject> findAllByOrderByCertificateIdAscSortOrderAscCodeAsc();

    boolean existsByCertificateIdAndCode(UUID certificateId, String code);

    boolean existsByCertificateIdAndCodeAndIdNot(UUID certificateId, String code, UUID id);
}
