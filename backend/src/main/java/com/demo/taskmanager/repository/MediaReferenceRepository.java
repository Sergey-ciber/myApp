package com.demo.taskmanager.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ссылки на медиафайлы, которые сейчас используются в базе.
 *
 * Медиа хранятся в нескольких element-collection таблицах и legacy-полях,
 * поэтому собираем их одним SQL-запросом. Возвращаются именно URL: разбор
 * имени файла и отсечение внешних ссылок делает сервис очистки.
 */
@Repository
public class MediaReferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public MediaReferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Все URL медиа, на которые ссылаются игры, уровни, подсказки, вопросы и аватары. */
    public Set<String> findReferencedUrls() {
        String sql = """
                select url from game_media
                union
                select url from level_media
                union
                select url from level_hint_media
                union
                select url from standard_question_media
                union
                select media_url from standard_questions where media_url is not null
                union
                select image_url from level_hints where image_url is not null
                union
                select avatar_url from users where avatar_url is not null
                union
                select avatar_url from teams where avatar_url is not null
                """;
        List<String> urls = jdbcTemplate.queryForList(sql, String.class);
        return new HashSet<>(urls);
    }
}
