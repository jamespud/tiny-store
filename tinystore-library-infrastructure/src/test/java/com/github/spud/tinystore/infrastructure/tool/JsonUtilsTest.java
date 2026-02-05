package com.github.spud.tinystore.infrastructure.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JsonUtils 工具类单元测试")
class JsonUtilsTest {

    @Test
    @DisplayName("序列化对象为JSON字符串 - 正常路径")
    void toJson_validObject_shouldReturnJsonString() {
        // Given
        TestPojo pojo = new TestPojo("testName", 123);
        
        // When
        String json = JsonUtils.toJson(pojo);
        
        // Then
        assertThat(json)
            .isNotNull()
            .contains("\"name\":\"testName\"")
            .contains("\"value\":123");
    }

    @Test
    @DisplayName("反序列化JSON为对象 - 正常路径")
    void fromJson_validJson_shouldReturnObject() {
        // Given
        String json = "{\"name\":\"testName\",\"value\":456}";
        
        // When
        TestPojo result = JsonUtils.fromJson(json, TestPojo.class);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("testName");
        assertThat(result.getValue()).isEqualTo(456);
    }

    @Test
    @DisplayName("反序列化JSON为Map - 正常路径")
    void parseMap_validJson_shouldReturnMap() {
        // Given
        String json = "{\"key1\":\"value1\",\"key2\":123}";
        
        // When
        Map<String, Object> result = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        
        // Then
        assertThat(result)
            .isNotNull()
            .containsEntry("key1", "value1")
            .containsEntry("key2", 123);
    }

    @Test
    @DisplayName("反序列化JSON为List - 正常路径")
    void parseList_validJson_shouldReturnList() {
        // Given
        String json = "[\"item1\",\"item2\",\"item3\"]";
        
        // When
        List<Object> result = JsonUtils.fromJson(json, new TypeReference<List<Object>>() {});
        
        // Then
        assertThat(result)
            .isNotNull()
            .hasSize(3)
            .contains("item1", "item2", "item3");
    }

    @Test
    @DisplayName("反序列化JSON - 泛型类型支持")
    void fromJson_withTypeReference_shouldReturnTypedCollection() {
        // Given
        String json = "[{\"name\":\"first\",\"value\":1},{\"name\":\"second\",\"value\":2}]";
        
        // When
        List<TestPojo> result = JsonUtils.fromJson(json, new TypeReference<List<TestPojo>>() {});
        
        // Then
        assertThat(result)
            .isNotNull()
            .hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("first");
        assertThat(result.get(1).getValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("反序列化非法JSON - 应抛出异常")
    void fromJson_invalidJson_shouldThrowException() {
        // Given
        String invalidJson = "{invalid json";
        
        // When & Then
        assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, TestPojo.class))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("序列化null对象 - 边界条件")
    void toJson_nullObject_shouldReturnNullString() {
        // When
        String result = JsonUtils.toJson(null);
        
        // Then
        assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("readTree - 解析JSON为树结构")
    void readTree_validJson_shouldReturnJsonNode() {
        // Given
        String json = "{\"nested\":{\"key\":\"value\"}}";
        
        // When
        var node = JsonUtils.readTree(json);
        
        // Then
        assertThat(node).isNotNull();
        assertThat(node.get("nested").get("key").asText()).isEqualTo("value");
    }

    // ========== 测试用POJO ==========
    static class TestPojo {
        private String name;
        private Integer value;

        public TestPojo() {
        }

        public TestPojo(String name, Integer value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }
    }
}
