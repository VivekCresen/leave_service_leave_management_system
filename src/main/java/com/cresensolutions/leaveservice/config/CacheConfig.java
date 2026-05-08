package com.cresensolutions.leaveservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;


@Configuration
@EnableCaching
public class CacheConfig {

    @Primary
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
           
            "userProfiles",
            "userByUsername",
            "userById",
            "activeUsers",
            "usersByRole",
            "usersByManager",
            "leaveBalances",
            "leaveApplications",
            "leaveTypes",
            "leavesByUser",
            "leavesByStatus",
            "pendingLeaves",
            "publicHolidays",
            "upcomingHolidays",
            "holidaysByDateRange",
            "chatbotAnswers",
            "chatbotSchema",
            "chatHistory",
            "countries",
            "phoneCodes",
            "systemConfig"
        );
        
        cacheManager.setCaffeine(defaultCaffeineBuilder());
        return cacheManager;
    }

   
    private Caffeine<Object, Object> defaultCaffeineBuilder() {
        return Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(1000)
            .recordStats();
    }


    @Bean
    public CacheManager shortLivedCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "onLeaveToday",
            "recentLeaveApplications",
            "dashboardStats"
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .recordStats());
        
        return cacheManager;
    }

 
    @Bean
    public CacheManager longLivedCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "leaveTypesLong",
            "publicHolidaysLong",
            "countriesLong",
            "systemConfigLong"
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(100)
            .recordStats());
        
        return cacheManager;
    }

    @Bean(name = "userProfileCaffeine")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> userProfileCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(500)
            .recordStats()
            .build();
    }


    @Bean(name = "leaveBalanceCaffeine")
    public com.github.benmanes.caffeine.cache.Cache<Long, Object> leaveBalanceCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats()
            .build();
    }

  
    @Bean(name = "chatbotAnswerCaffeine")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> chatbotAnswerCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .recordStats()
            .build();
    }


    @Bean(name = "schemaCaffeine")
    public com.github.benmanes.caffeine.cache.Cache<String, String> schemaCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10)
            .recordStats()
            .build();
    }
}
