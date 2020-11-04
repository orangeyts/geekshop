package co.jueyi.geekshop.types.product;

import co.jueyi.geekshop.types.common.PaginatedList;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on Nov, 2020 by @author bobo
 */
@Data
public class ProductVariantList implements PaginatedList<ProductVariant> {
    public List<ProductVariant> items = new ArrayList<>();
    public Integer totalItems;
}