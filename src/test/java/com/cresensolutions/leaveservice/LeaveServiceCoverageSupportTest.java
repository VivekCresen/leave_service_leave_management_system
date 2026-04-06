package com.cresensolutions.leaveservice;

import com.cresensolutions.leaveservice.controller.HelloController;
import com.cresensolutions.leaveservice.controller.LeaveController;
import com.cresensolutions.leaveservice.dto.ApiErrorResponse;
import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveTypeRequest;
import com.cresensolutions.leaveservice.dto.LeaveResponse;
import com.cresensolutions.leaveservice.dto.LeaveTypeResponse;
import com.cresensolutions.leaveservice.exception.GlobalExceptionHandler;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.EmployeeLeave;
import com.cresensolutions.leaveservice.model.LeaveRecord;
import com.cresensolutions.leaveservice.model.LeaveType;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.service.LeaveService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveServiceCoverageSupportTest {

    @Test
    void shouldDelegateControllerMethods() {
        LeaveService leaveService = mock(LeaveService.class);
        LeaveController controller = new LeaveController(leaveService);
        HelloController helloController = new HelloController();

        LeaveResponse leaveResponse = new LeaveResponse(1L, 2L, "Vivek", "vivek@cresen.com", 3, "CASUAL", LocalDate.now(), LocalDate.now().plusDays(1), "Trip", "Ok", "{}", true, LocalDate.now(), LocalDate.now());
        LeaveTypeResponse leaveTypeResponse = new LeaveTypeResponse(3, "Casual", "CASUAL", "Desc", 10, Instant.now(), Instant.now());
        Page<LeaveResponse> page = new PageImpl<>(List.of(leaveResponse));
        CreateLeaveRequest leaveRequest = new CreateLeaveRequest(2L, 3, LocalDate.now(), LocalDate.now().plusDays(1), "Trip", "Ok", "{}", true);
        CreateLeaveTypeRequest leaveTypeRequest = new CreateLeaveTypeRequest("Casual", "CASUAL", "Desc", 10);

        when(leaveService.createLeave(leaveRequest)).thenReturn(leaveResponse);
        when(leaveService.getAllLeaves(0, 50)).thenReturn(page);
        when(leaveService.getLeaveById(1L)).thenReturn(leaveResponse);
        when(leaveService.getLeavesByUserId(2L, 0, 50)).thenReturn(page);
        when(leaveService.getLeaveTypes()).thenReturn(List.of(leaveTypeResponse));
        when(leaveService.createLeaveType(leaveTypeRequest)).thenReturn(leaveTypeResponse);
        when(leaveService.updateLeaveType(3, leaveTypeRequest)).thenReturn(leaveTypeResponse);

        assertSame(leaveResponse, controller.createLeave(leaveRequest));
        assertSame(page, controller.getAllLeaves(0, 50));
        assertSame(leaveResponse, controller.getLeaveById(1L));
        assertSame(page, controller.getLeavesByUserId(2L, 0, 50));
        assertEquals(List.of(leaveTypeResponse), controller.getLeaveTypes());
        assertSame(leaveTypeResponse, controller.createLeaveType(leaveTypeRequest));
        assertSame(leaveTypeResponse, controller.updateLeaveType(3, leaveTypeRequest));
        controller.deleteLeaveType(3);

        verify(leaveService).deleteLeaveType(3);
        assertEquals("Hello from Leave Service microservice", helloController.hello());
    }

    @Test
    void shouldHandleLeaveExceptions() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertEquals(HttpStatus.NOT_FOUND, handler.handleResourceNotFound(new ResourceNotFoundException("missing")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleIllegalArgument(new IllegalArgumentException("bad")).getStatusCode());

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "Email invalid"));
        bindingResult.addError(new ObjectError("request", "Object invalid"));
        ApiErrorResponse invalidResponse = handler.handleMethodArgumentNotValid(new MethodArgumentNotValidException(null, bindingResult)).getBody();
        assertEquals(2, invalidResponse.details().size());

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path propertyPath = mock(Path.class);
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(propertyPath.toString()).thenReturn("createLeave.userId");
        when(violation.getMessage()).thenReturn("must not be null");
        ApiErrorResponse constraintResponse = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation))).getBody();
        assertTrue(constraintResponse.details().get(0).contains("createLeave.userId"));
    }

    @Test
    void shouldCoverLeaveModelsAndServiceHelpers() throws Exception {
        Constructor<UserProfile> userProfileConstructor = UserProfile.class.getDeclaredConstructor();
        userProfileConstructor.setAccessible(true);
        UserProfile userProfile = userProfileConstructor.newInstance();
        setField(userProfile, "id", 10L);
        setField(userProfile, "companyId", "CRESEN010");
        setField(userProfile, "userName", "vivek");
        setField(userProfile, "fullName", "Vivek");
        setField(userProfile, "emailId", "vivek@cresen.com");
        setField(userProfile, "userPswd", "secret");
        setField(userProfile, "role", "EMPLOYEE");
        setField(userProfile, "roleId", 3L);
        setField(userProfile, "gender", "Male");
        setField(userProfile, "active", true);
        setField(userProfile, "createDate", Instant.now());
        setField(userProfile, "updateDate", Instant.now());
        setField(userProfile, "createdBy", "admin");
        setField(userProfile, "updatedBy", "admin");
        setField(userProfile, "lastLogin", Instant.now());

        Constructor<EmployeeLeave> employeeLeaveConstructor = EmployeeLeave.class.getDeclaredConstructor();
        employeeLeaveConstructor.setAccessible(true);
        EmployeeLeave employeeLeave = employeeLeaveConstructor.newInstance();
        setField(employeeLeave, "id", 11L);
        setField(employeeLeave, "user", userProfile);
        setField(employeeLeave, "fullName", "Vivek");
        setField(employeeLeave, "emailId", "vivek@cresen.com");
        setField(employeeLeave, "leaves", "{}");
        setField(employeeLeave, "gender", "Male");
        setField(userProfile, "employeeLeave", employeeLeave);
        assertEquals(11L, employeeLeave.getId());
        assertEquals(10L, employeeLeave.getUserId());
        assertSame(employeeLeave, userProfile.getEmployeeLeave());

        Constructor<LeaveType> leaveTypeConstructor = LeaveType.class.getDeclaredConstructor();
        leaveTypeConstructor.setAccessible(true);
        LeaveType fallbackType = leaveTypeConstructor.newInstance();
        setField(fallbackType, "leaveName", "Casual Leave");
        setField(fallbackType, "leaveUniqueName", " ");
        invoke(fallbackType, "onCreate");
        invoke(fallbackType, "onUpdate");
        fallbackType.updateDetails("Optional", "OPTIONAL", "Desc", 5);
        assertEquals("Optional", fallbackType.getLeaveName());
        assertEquals("OPTIONAL", fallbackType.getDisplayName());

        LeaveType linkedType = new LeaveType(3, "Sick Leave", "SICK");
        LeaveRecord leaveRecord = new LeaveRecord(userProfile, linkedType, LocalDate.now(), LocalDate.now().plusDays(1), "Trip", "Ok", "{}", true);
        invoke(leaveRecord, "onCreate");
        invoke(leaveRecord, "onUpdate");
        assertEquals(10L, leaveRecord.getUserId());
        assertEquals(3, leaveRecord.getLeaveTypeId());
        assertTrue(userProfile.getLeaveRecords().contains(leaveRecord));
        assertTrue(linkedType.getLeaveRecords().contains(leaveRecord));

        leaveRecord.assignUser(null);
        leaveRecord.assignLeaveType(null);
        assertEquals("vivek@cresen.com", leaveRecord.getEmailId());
        assertEquals("SICK", leaveRecord.getLeaveType());

        assertEquals("vivek", userProfile.getUserName());
        assertEquals("vivek@cresen.com", userProfile.getEmailId());
        assertEquals("secret", userProfile.getUserPswd());
        assertEquals("EMPLOYEE", userProfile.getRole());
        assertEquals(3L, userProfile.getRoleId());
        assertEquals("Male", userProfile.getGender());
        assertTrue(userProfile.isActive());
        assertEquals("admin", userProfile.getCreatedBy());
        assertEquals("admin", userProfile.getUpdatedBy());

        com.cresensolutions.leaveservice.repository.LeaveRepository leaveRepository = mock(com.cresensolutions.leaveservice.repository.LeaveRepository.class);
        com.cresensolutions.leaveservice.repository.UserProfileRepository userProfileRepository = mock(com.cresensolutions.leaveservice.repository.UserProfileRepository.class);
        com.cresensolutions.leaveservice.repository.LeaveTypeRepository leaveTypeRepository = mock(com.cresensolutions.leaveservice.repository.LeaveTypeRepository.class);
        com.cresensolutions.leaveservice.service.LeaveServiceImpl leaveService = new com.cresensolutions.leaveservice.service.LeaveServiceImpl(leaveRepository, userProfileRepository, leaveTypeRepository);

        assertEquals("OPTIONAL_LEAVE", invoke(leaveService, "normalizeUniqueName", String.class, "optional leave"));
        assertNull(invoke(leaveService, "normalizeOptionalValue", String.class, "   "));
        assertThrowsWrappedIllegalArgument(() -> invoke(leaveService, "normalizeRequiredValue", new Class[]{String.class, String.class}, new Object[]{"   ", "required"}));
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String methodName, Class<?> parameterType, Object arg) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterType);
        method.setAccessible(true);
        return method.invoke(target, arg);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void assertThrowsWrappedIllegalArgument(ThrowingRunnable runnable) {
        Exception exception = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, runnable::run);
        Throwable cause = exception.getCause();
        assertNotNull(cause);
        assertEquals(IllegalArgumentException.class, cause.getClass());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
