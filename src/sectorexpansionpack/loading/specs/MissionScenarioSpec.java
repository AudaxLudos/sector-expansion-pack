package sectorexpansionpack.loading.specs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MissionScenarioSpec {
    private String missionId;
    private String scenarioId;

    private float frequency;
    private float duration;

    private String creditReward;

    private String type;

    private HashSet<String> complications = new HashSet<>();
    private HashSet<String> tags = new HashSet<>();

    private String data1;
    private String data2;
    private String data3;

    public String getMissionId() {
        return this.missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getScenarioId() {
        return this.scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public float getFrequency() {
        return this.frequency;
    }

    public void setFrequency(float frequency) {
        this.frequency = frequency;
    }

    public float getDuration() {
        return this.duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public String getCreditReward() {
        return this.creditReward;
    }

    public void setCreditReward(String creditReward) {
        this.creditReward = creditReward;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getComplications() {
        return new ArrayList<>(this.complications);
    }

    public void setComplications(HashSet<String> complications) {
        this.complications = complications;
    }

    public List<String> getTags() {
        return new ArrayList<>(this.tags);
    }

    public void setTags(HashSet<String> tags) {
        this.tags = tags;
    }

    public String getData1() {
        return this.data1;
    }

    public void setData1(String data1) {
        this.data1 = data1;
    }

    public String getData2() {
        return this.data2;
    }

    public void setData2(String data2) {
        this.data2 = data2;
    }

    public String getData3() {
        return this.data3;
    }

    public void setData3(String data3) {
        this.data3 = data3;
    }
}
