package com.omino.shopai.mapper;

import com.omino.shopai.entity.TicketLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TicketLogMapper {

    @Insert("""
            INSERT INTO ticket_log
                (id, ticket_id, kind, actor_type, actor_user_id, actor_sso_sub, from_value, to_value, content, payload, created_at)
            VALUES
                (#{id}, #{ticketId}, #{kind}, #{actorType}, #{actorUserId}, #{actorSsoSub}, #{fromValue}, #{toValue}, #{content}, CAST(#{payload} AS jsonb), #{createdAt})
            """)
    int insert(TicketLog row);

    @Select("""
            SELECT id, ticket_id AS ticketId, kind, actor_type AS actorType, actor_user_id AS actorUserId,
                   actor_sso_sub AS actorSsoSub, from_value AS fromValue, to_value AS toValue, content,
                   payload::text AS payload, created_at AS createdAt
            FROM ticket_log
            WHERE ticket_id = #{ticketId}
            ORDER BY created_at
            """)
    List<TicketLog> listByTicket(String ticketId);
}
