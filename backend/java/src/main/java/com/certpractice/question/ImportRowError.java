package com.certpractice.question;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "import_row_error")
public class ImportRowError {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "field_name", length = 64)
    private String fieldName;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;
}
