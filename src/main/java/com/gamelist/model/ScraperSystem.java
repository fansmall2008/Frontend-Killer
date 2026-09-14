package com.gamelist.model;

public class ScraperSystem {
    private Long id;
    private Integer systemId;
    private Integer parentId;
    
    // 主名称字段
    private String name;
    private String nameEn;
    private String nameFr;
    private String nameJp;
    private String nameCn;
    
    // noms对象中的所有字段
    private String nomEu;
    private String nomLaunchbox;
    private String nomHyperspin;
    private String nomRecalbox;
    private String nomRetropie;
    private String nomsCommun;
    
    // 其他基本信息
    private String company;
    private String type;
    private Integer releaseYear;
    private Integer endYear;
    private String romType;
    private String supportType;
    private String extensions;
    
    // 图标和时间字段
    private String iconUrl;
    private Boolean mediaScraped;
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSystemId() {
        return systemId;
    }

    public void setSystemId(Integer systemId) {
        this.systemId = systemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameFr() {
        return nameFr;
    }

    public void setNameFr(String nameFr) {
        this.nameFr = nameFr;
    }

    public String getNameJp() {
        return nameJp;
    }

    public void setNameJp(String nameJp) {
        this.nameJp = nameJp;
    }

    public String getNameCn() {
        return nameCn;
    }

    public void setNameCn(String nameCn) {
        this.nameCn = nameCn;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public void setEndYear(Integer endYear) {
        this.endYear = endYear;
    }

    public String getRomType() {
        return romType;
    }

    public void setRomType(String romType) {
        this.romType = romType;
    }

    public String getSupportType() {
        return supportType;
    }

    public void setSupportType(String supportType) {
        this.supportType = supportType;
    }

    public String getExtensions() {
        return extensions;
    }

    public void setExtensions(String extensions) {
        this.extensions = extensions;
    }

    public String getNomEu() {
        return nomEu;
    }

    public void setNomEu(String nomEu) {
        this.nomEu = nomEu;
    }

    public String getNomLaunchbox() {
        return nomLaunchbox;
    }

    public void setNomLaunchbox(String nomLaunchbox) {
        this.nomLaunchbox = nomLaunchbox;
    }

    public String getNomHyperspin() {
        return nomHyperspin;
    }

    public void setNomHyperspin(String nomHyperspin) {
        this.nomHyperspin = nomHyperspin;
    }

    public String getNomRecalbox() {
        return nomRecalbox;
    }

    public void setNomRecalbox(String nomRecalbox) {
        this.nomRecalbox = nomRecalbox;
    }

    public String getNomRetropie() {
        return nomRetropie;
    }

    public void setNomRetropie(String nomRetropie) {
        this.nomRetropie = nomRetropie;
    }

    public String getNomsCommun() {
        return nomsCommun;
    }

    public void setNomsCommun(String nomsCommun) {
        this.nomsCommun = nomsCommun;
    }

    public Boolean getMediaScraped() {
        return mediaScraped;
    }

    public void setMediaScraped(Boolean mediaScraped) {
        this.mediaScraped = mediaScraped;
    }
}