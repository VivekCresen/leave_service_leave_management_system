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
public class LoggingAspect {

    @Pointcut("within(com.cresensolutions.leaveservice.controller..*)")
    public void controllerLayer() {}

    @Pointcut("within(com.cresensolutions.leaveservice.service.Impl..*)")
    public void serviceLayer() {}

    @Around("controllerLayer() || serviceLayer()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        if (!log.isDebugEnabled()) {
            return pjp.proceed();
        }

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String className  = sig.getDeclaringType().getSimpleName();
        String methodName = sig.getName();

        log.debug("[{}] → {}({})", className, methodName, formatArgs(pjp.getArgs()));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[{}] ← {}() returned {} [{}ms]",
                    className, methodName,
                    result == null ? "null" : result.getClass().getSimpleName(),
                    elapsed);
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[{}] ← {}() threw {} [{}ms]",
                    className, methodName, t.getClass().getSimpleName(), elapsed);
            throw t;
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(safeToString(args[i]));
        }
        return sb.toString();
    }

    private String safeToString(Object arg) {
        if (arg == null) return "null";
        String s = arg.toString();
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
