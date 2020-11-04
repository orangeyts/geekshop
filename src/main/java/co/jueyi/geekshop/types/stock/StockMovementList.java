package co.jueyi.geekshop.types.stock;

import co.jueyi.geekshop.types.common.PaginatedList;
import lombok.Data;

import java.util.List;

/**
 * Created on Nov, 2020 by @author bobo
 */
@Data
public class StockMovementList implements PaginatedList<StockMovement> {
    public List<StockMovement> items;
    public Integer totalItems;
}