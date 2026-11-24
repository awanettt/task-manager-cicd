package by.edu.example.taskmanager.web;

import by.edu.example.taskmanager.dto.ApiError;
import by.edu.example.taskmanager.dto.CreateTaskRequest;
import by.edu.example.taskmanager.dto.UpdateTaskPriorityRequest;
import by.edu.example.taskmanager.dto.UpdateTaskStatusRequest;
import by.edu.example.taskmanager.model.Task;
import by.edu.example.taskmanager.model.TaskPriority;
import by.edu.example.taskmanager.model.TaskStatus;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты проверяют HTTP-контракт, JSON и обработку ошибок.
 *
 * Важно: JDK HttpURLConnection не поддерживает метод PATCH.
 * Поэтому TestRestTemplate настраивается на Apache HttpClient 5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskControllerIT {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // HttpClient 5 умеет PATCH; стандартный JDK-клиент — нет.
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault());

        RestTemplateBuilder builder = new RestTemplateBuilder()
                .rootUri("http://localhost:" + port)
                .requestFactory(() -> requestFactory);

        this.restTemplate = new TestRestTemplate(builder);
    }

    @Test
    void createAndReadTaskThroughHttp() {
        ResponseEntity<Task> createdResponse = restTemplate.postForEntity(
                "/api/tasks",
                new CreateTaskRequest("CI pipeline", "Created by integration test"),
                Task.class);
        assertEquals(HttpStatus.CREATED, createdResponse.getStatusCode());
        Task created = createdResponse.getBody();
        assertNotNull(created);
        assertTrue(created.id() > 0);
        assertEquals("CI pipeline", created.title());
        assertEquals(TaskPriority.NORMAL, created.priority());
        assertNotNull(createdResponse.getHeaders().getLocation());

        ResponseEntity<Task> readResponse = restTemplate.getForEntity(
                "/api/tasks/" + created.id(), Task.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode());
        assertNotNull(readResponse.getBody());
        assertEquals(created.id(), readResponse.getBody().id());
    }

    @Test
    void createSupportsAllThreePriorityValues() {
        for (TaskPriority priority : TaskPriority.values()) {
            ResponseEntity<Task> response = restTemplate.postForEntity(
                    "/api/tasks", new CreateTaskRequest("Task " + priority, "", priority), Task.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(priority, response.getBody().priority());
        }
    }

    @Test
    void createWithoutPriorityUsesNormal() {
        ResponseEntity<Task> response = restTemplate.postForEntity(
                "/api/tasks", new CreateTaskRequest("Default priority", "", null), Task.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TaskPriority.NORMAL, response.getBody().priority());
    }

    @Test
    void filterByPriorityReturnsOnlyMatchingTasks() {
        restTemplate.postForEntity("/api/tasks",
                new CreateTaskRequest("High filter test", "", TaskPriority.HIGH), Task.class);
        ResponseEntity<Task[]> response = restTemplate.getForEntity(
                "/api/tasks?priority=HIGH", Task[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length >= 1);
        assertTrue(Arrays.stream(response.getBody())
                .allMatch(task -> task.priority() == TaskPriority.HIGH));
    }

    @Test
    void unknownPriorityInQueryReturnsBadRequestApiError() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                "/api/tasks?priority=URGENT", ApiError.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertTrue(response.getBody().message().contains("LOW, NORMAL, HIGH"));
    }

    @Test
    void patchChangesPriorityAndPreservesStatus() {
        Task created = restTemplate.postForObject("/api/tasks",
                new CreateTaskRequest("Change priority", "", TaskPriority.LOW), Task.class);
        assertNotNull(created);
        restTemplate.exchange("/api/tasks/" + created.id() + "/status", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS)), Task.class);

        ResponseEntity<Task> response = restTemplate.exchange(
                "/api/tasks/" + created.id() + "/priority", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateTaskPriorityRequest(TaskPriority.HIGH)), Task.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TaskPriority.HIGH, response.getBody().priority());
        assertEquals(TaskStatus.IN_PROGRESS, response.getBody().status());
    }

    @Test
    void patchNullPriorityReturnsBadRequest() {
        Task created = restTemplate.postForObject("/api/tasks",
                new CreateTaskRequest("Null priority", ""), Task.class);
        assertNotNull(created);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                "/api/tasks/" + created.id() + "/priority", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateTaskPriorityRequest(null)), ApiError.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void unknownPriorityInJsonReturnsBadRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"title\":\"Invalid priority\",\"description\":\"\",\"priority\":\"URGENT\"}";
        ResponseEntity<ApiError> response = restTemplate.exchange(
                "/api/tasks", HttpMethod.POST, new HttpEntity<>(json, headers), ApiError.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
    }

    @Test
    void blankTitleReturnsBadRequest() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                "/api/tasks", new CreateTaskRequest("   ", "Invalid input"), ApiError.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
    }

    @Test
    void unknownTaskReturnsNotFound() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                "/api/tasks/987654321", ApiError.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
    }

    @Test
    void patchChangesTaskStatusWithoutLosingPriority() {
        Task created = restTemplate.postForObject("/api/tasks",
                new CreateTaskRequest("Update status", "", TaskPriority.HIGH), Task.class);
        assertNotNull(created);
        ResponseEntity<Task> response = restTemplate.exchange(
                "/api/tasks/" + created.id() + "/status", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateTaskStatusRequest(TaskStatus.DONE)), Task.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TaskStatus.DONE, response.getBody().status());
        assertEquals(TaskPriority.HIGH, response.getBody().priority());
    }
}
