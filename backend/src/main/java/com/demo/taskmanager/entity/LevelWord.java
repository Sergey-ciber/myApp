package com.demo.taskmanager.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "level_words", uniqueConstraints = @UniqueConstraint(columnNames = {"level_id", "column_num", "position"}))
public class LevelWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private GameLevel level;

    @Column(name = "column_num", nullable = false)
    private int columnNum;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 60)
    private String word;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GameLevel getLevel() { return level; }
    public void setLevel(GameLevel level) { this.level = level; }
    public int getColumnNum() { return columnNum; }
    public void setColumnNum(int columnNum) { this.columnNum = columnNum; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
}
