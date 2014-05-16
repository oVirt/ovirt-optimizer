package org.ovirt.optimizer.common;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ScoreResult {
    int softScore;
    int hardScore;

    public ScoreResult() {
    }

    public int getSoftScore() {
        return softScore;
    }

    public void setSoftScore(int softScore) {
        this.softScore = softScore;
    }

    public int getHardScore() {
        return hardScore;
    }

    public void setHardScore(int hardScore) {
        this.hardScore = hardScore;
    }
}
