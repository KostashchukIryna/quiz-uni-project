package com.course.app.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class HostRatingId implements Serializable {

    private String period;
    private String username;

    public HostRatingId(String period, String username) {
        this.period = period;
        this.username = username;
    }
}
