package com.kant.llm.examples.dto;

import java.util.List;

/**
 * 歌手的歌曲
 * @param singer
 * @param songs
 */
public record SingerSongs(String singer, List<String> songs) {

}