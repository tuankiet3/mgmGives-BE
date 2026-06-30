package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findAllByDeletedAtIsNullOrderByNameAsc();

    Optional<Category> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);
}
