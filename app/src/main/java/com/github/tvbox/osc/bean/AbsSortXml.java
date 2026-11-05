package com.github.tvbox.osc.bean;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.io.Serializable;
import java.util.List;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
@XStreamAlias("rss")
public class AbsSortXml implements Serializable {
    @XStreamAlias("class")
    public MovieSort classes;

    @XStreamAlias("list")
    public Movie list;

    public List<Movie.Video> videoList;

    /** 本次分类结果所属的源 key：切源后用于丢弃上一源的迟到响应（不参与 XML/JSON 序列化） */
    @XStreamOmitField
    public transient String sourceKey;
}