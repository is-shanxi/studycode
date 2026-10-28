package com.certpractice.aigateway;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigVersionRepository extends JpaRepository<SystemConfigVersion, UUID> {

    List<SystemConfigVersion> findByConfigKeyOrderByVersionNoDesc(String configKey);

    Optional<SystemConfigVersion> findFirstByConfigKeyOrderByVersionNoDesc(String configKey);
}
