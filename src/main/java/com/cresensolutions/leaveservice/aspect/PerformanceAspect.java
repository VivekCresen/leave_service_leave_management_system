package com.cresensolutions.leaveservice.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PerformanceAspect {

    private static final long WARN_THRESHOLD_MS = 500;

    @Pointcut("within(com.cresensolutions.leaveservice.service.Impl..*)")
    public void serviceLayer() {}

    @Around("serviceLayer()")
    public Object monitorPerformance(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > WARN_THRESHOLD_MS) {
                MethodSignature sig = (MethodSignature) pjp.getSignature();
                log.warn("[PERF] {}.{}() took {}ms — exceeds {}ms threshold",
                        sig.getDeclaringType().getSimpleName(),
                        sig.getName(),
                        elapsed,
                        WARN_THRESHOLD_MS);
            }
        }
    }
}
