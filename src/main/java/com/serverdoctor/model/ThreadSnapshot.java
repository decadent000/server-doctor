package com.serverdoctor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThreadSnapshot {

    private final String name;
    private final String nid;
    private final String state;
    private final List<String> frames;

    public ThreadSnapshot(String name, String nid, String state, List<String> frames) {
        this.name = name;
        this.nid = nid;
        this.state = state;
        this.frames = Collections.unmodifiableList(new ArrayList<String>(frames));
    }

    public String getName() {
        return name;
    }

    public String getNid() {
        return nid;
    }

    public String getState() {
        return state;
    }

    public List<String> getFrames() {
        return frames;
    }

    public String identity() {
        if (nid != null && !nid.isEmpty()) {
            return name + "|" + nid;
        }
        return name;
    }
}
