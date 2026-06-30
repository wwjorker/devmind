package com.devmind;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

class DevMindApplicationMapperScanTest {

    @Test
    void mapperScanOnlyRegistersMybatisPlusBaseMappers() {
        MapperScan mapperScan = DevMindApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.basePackages()).containsExactly("com.devmind.module");
        assertThat(mapperScan.markerInterface()).isEqualTo(BaseMapper.class);
    }
}
