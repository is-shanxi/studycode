package com.certpractice.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameNormalized(String usernameNormalized);

    boolean existsByUsernameNormalized(String usernameNormalized);

    List<AppUser> findByStatus(AppUser.Status status);

    @Query("""
            select user from AppUser user
            where (:status is null or user.status = :status)
              and (
                  :keyword is null
                  or lower(user.username) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(user.nickname, '')) like lower(concat('%', :keyword, '%'))
              )
            order by user.createdAt desc
            """)
    Page<AppUser> search(
            @Param("keyword") String keyword,
            @Param("status") AppUser.Status status,
            Pageable pageable);
}
