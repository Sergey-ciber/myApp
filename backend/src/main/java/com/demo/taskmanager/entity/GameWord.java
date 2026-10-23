package com.demo.taskmanager.entity;

import jakarta.persistence.*;

/**
 * Одно слово пирамиды: колонка (1..5) и позиция внутри колонки (1..16).
 * Подсказка для слова i в колонке N+1 — это пара слов (2i-1, 2i) из колонки N.
 */
@Entity
@Table(name = "game_words",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_words_cell",
                columnNames = {"game_id", "column_num", "position"}))
public class GameWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "column_num", nullable = false)
    private int columnNum;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 60)
    private String word;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public int getColumnNum() { return columnNum; }
    public void setColumnNum(int columnNum) { this.columnNum = columnNum; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
}
