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
     */
    UserCategoryResponse toUserResponse(Category category);

    List<UserCategoryResponse> toUserResponseList(List<Category> categories);

    /**
     * Maps AdminCreateCategoryRequest DTO to Category Entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Category toEntity(AdminCreateCategoryRequest request);

    /**
     * Merges update request details into an existing Category entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(AdminUpdateCategoryRequest request, @MappingTarget Category category);

    CategoryResponse toResponse(Category category);
}
