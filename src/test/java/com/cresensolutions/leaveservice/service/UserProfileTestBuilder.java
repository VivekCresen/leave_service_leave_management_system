package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.UserProfile;

import java.lang.reflect.Field;

final class UserProfileTestBuilder {

    private Long id;
    private String fullName;
    private String emailId;
    private boolean active;

    UserProfileTestBuilder id(Long id) {
        this.id = id;
        return this;
    }

    UserProfileTestBuilder fullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    UserProfileTestBuilder emailId(String emailId) {
        this.emailId = emailId;
        return this;
    }

    UserProfileTestBuilder active(boolean active) {
        this.active = active;
        return this;
    }

    UserProfile build() {
        UserProfile userProfile = new UserProfile();
        setField(userProfile, "id", id);
        setField(userProfile, "fullName", fullName);
        setField(userProfile, "emailId", emailId);
        setField(userProfile, "active", active);
        return userProfile;
    }

    private void setField(UserProfile target, String fieldName, Object value) {
        try {
            Field field = UserProfile.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to build test user profile", exception);
        }
    }
}
