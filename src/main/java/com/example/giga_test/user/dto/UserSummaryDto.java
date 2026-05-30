package com.example.giga_test.user.dto;

import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.User;

public record UserSummaryDto(Long id, String name, String username, RoleName role) {
    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(user.getId(), user.getName(), user.getUsername(), user.getRole());
    }
}
