package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.category.*;
import com.mgmtp.gives.entity.Category;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {
    /**
     * Maps Category Entity to AdminCategoryResponse DTO.
     */
    @Mapping(target = "campaignsCount", expression = "java(category.getCampaigns() != null ? (long) category.getCampaigns().size() : 0L)")
    AdminCategoryResponse toAdminResponse(Category category);

    List<AdminCategoryResponse> toAdminResponseList(List<Category> categories);

    /**
     * Maps Category Entity to UserCategoryResponse DTO.
     * Status is intentionally NOT included in UserCategoryResponse — it is internal workflow data
     * and must never leak to the public API. MapStruct automatically skips unmapped target fields
     * that don't exist in the target record.
     */
    UserCategoryResponse toUserResponse(Category category);

    List<UserCategoryResponse> toUserResponseList(List<Category> categories);

    /**
     * Maps AdminCreateCategoryRequest DTO to Category Entity.
     * Sets target status to APPROVED by default (since admins create approved categories).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "status", constant = "APPROVED")
    Category toEntity(AdminCreateCategoryRequest request);

    /**
     * Maps UserSuggestCategoryRequest DTO to Category Entity.
     * Sets target status to PENDING by default.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    Category toEntity(UserSuggestCategoryRequest request);

    /**
     * Merges update request details into an existing Category entity.
     * Ignored null values from the request (such as optional status changes) to avoid overwriting existing values.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaigns", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(AdminUpdateCategoryRequest request, @MappingTarget Category category);

    CategoryResponse toResponse(Category category);
}
