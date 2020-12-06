/*
 * Copyright (c) 2020 掘艺网络(jueyi.co).
 * All rights reserved.
 */

package co.jueyi.geekshop.data_import;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on Nov, 2020 by @author bobo
 */
@Data
public class CollectionDefinition {
    private String name;
    private String description;
    private String slug;
    private boolean privateOnly;
    private List<FacetValueCollectionFilterDefinition> filters = new ArrayList<>();
    private String parentName;
    private List<String> assetPaths = new ArrayList<>();
}