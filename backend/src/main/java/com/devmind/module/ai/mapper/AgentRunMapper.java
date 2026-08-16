package com.devmind.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmind.module.ai.entity.AgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {

    @Select("""
            SELECT *
            FROM agent_run
            WHERE id = #{runId}
              AND user_id = #{userId}
            FOR UPDATE
            """)
    AgentRun selectOwnedForUpdate(@Param("userId") Long userId, @Param("runId") Long runId);
}
