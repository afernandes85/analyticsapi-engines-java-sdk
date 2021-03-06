package factset.analyticsapi.engines;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.formula.eval.NotImplementedException;

import com.factset.protobuf.stach.NullValues;
import com.factset.protobuf.stach.MetadataItemProto.MetadataItem;
import com.factset.protobuf.stach.PackageProto.Package;
import com.factset.protobuf.stach.table.DataTypeProto.DataType;
import com.factset.protobuf.stach.table.SeriesDataProto.SeriesData;
import com.factset.protobuf.stach.table.SeriesDefinitionProto.SeriesDefinition;
import com.factset.protobuf.stach.table.TableProto.Table;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;

/**
 * The purpose of this class is to provide the helper methods for converting
 *  stach to Tabular format.
 */
public class StachExtensions {
   /**
   * This class provides helper method for returning the data from the SeriesData object by handling the null values.
   */
  private static class SeriesDataHelper {
     /**
     * The purpose of this function is to return the value from the provided SeriesData object.
     * @param seriesData : The data of a series. A series is how a column of data is represented.
     * @param dataType : The type of data in a series.
     * @param index : index can be referred as a key which is used to access the value inside list.
     * @param nullFormat : nullFormat is used to render a null value with a special string, like -- or @NA.
     * @return Returns data object from the SeriesData.
     */
    private static Object getValueHelper(SeriesData seriesData, DataType dataType, int index,
                                        String nullFormat) {
      if (dataType == DataType.STRING) {
        String value = seriesData.getStringArray().getValues(index);
        return NullValues.STRING.equals(value) ? nullFormat : value;
      } else if (dataType == DataType.DOUBLE) {
        double value = seriesData.getDoubleArray().getValues(index);
        return Double.isNaN(value) ? nullFormat : value;
      } else if (dataType == DataType.BOOL) {
        return seriesData.getBoolArray().getValues(index);
      } else if (dataType == DataType.DURATION) {
        Duration value = seriesData.getDurationArray().getValues(index);
        return NullValues.DURATION.equals(value) ? nullFormat : value;
      } else if (dataType == DataType.FLOAT) {
        float value = seriesData.getFloatArray().getValues(index);
        return Float.isNaN(value) ? nullFormat : value;
      } else if (dataType == DataType.INT32) {
        int value = seriesData.getInt32Array().getValues(index);
        return NullValues.INT32 == value ? nullFormat : value;
      } else if (dataType == DataType.INT64) {
        long value = seriesData.getInt64Array().getValues(index);
        return NullValues.INT64 == value ? nullFormat : value;
      } else if (dataType == DataType.TIMESTAMP) {
        Timestamp value = seriesData.getTimestampArray().getValues(index);
        return NullValues.TIMESTAMP.equals(value) ? nullFormat : value;
      } else {
        throw new NotImplementedException(dataType + " is not implemented");
      }
    }
  }
  /**
   * Represents the custom model class for returning the Row.
   */
  public static class Row {
    private boolean isHeader;
    private List<String> Cells = new ArrayList<String>();

    public boolean isHeader() {
      return isHeader;
    }

    public List<String> getCells() {
      return Cells;
    }

    public void setCells(List<String> cells) {
      this.Cells = cells;
    }

    public void setHeader(boolean isHeader) {
      this.isHeader = isHeader;
    }
  }
  /**
   * Represents the custom model class for returning the table data.
   */
  public static class TableData {
    private List<Row> Rows = new ArrayList<Row>();
    private Map<String, String> metadata = new HashMap<String, String>();

    public List<Row> getRows() {
      return Rows;
    }

    public void setRows(List<Row> rows) {
      this.Rows = rows;
    }

    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> map) {
      this.metadata = map;
    }
  }
  /**
   * The purpose of this function is to convert stach to Tabular format.
   * @param packageObj : Stach Data which is represented as a Package object.
   * @return Returns a list of tables for a given stach data.
   */
  public static List<TableData> convertToTableFormat(Package packageObj) {
    List<TableData> tables = new ArrayList<TableData>();
    for (String primaryTableId : packageObj.getPrimaryTableIdsList()) {
      tables.add(generateTable(packageObj, primaryTableId));
    }
    return tables;
  }
  /**
   * The purpose of this function is to generate Table for a given table id in the provided stach data through the package.
   * @param packageObj : Stach Data which is represented as a Package object.
   * @param primaryTableId : Refers to the id for a particular table inside a package.
   * @return Returns the generated Table from the package provided.
   */
  private static TableData generateTable(Package packageObj, String primaryTableId) {
    Map<String, Table> tablesMap = packageObj.getTablesMap();
    Table primaryTable = tablesMap.get(primaryTableId);
    String headerId = primaryTable.getDefinition().getHeaderTableId();
    Table headerTable = tablesMap.get(headerId);
    int headerRowCount = headerTable.getData().getRowsCount();
    int rowsCount = primaryTable.getData().getRowsCount();

    TableData table = new TableData();

    // Construct the column headers by considering dimension columns and header
    // rows.
    List<SeriesDefinition> headerTableSeriesDefinitions = headerTable.getDefinition().getColumnsList();
    List<SeriesDefinition> primaryTableSeriesDefinitions = primaryTable.getDefinition().getColumnsList();

    Map<String, SeriesData> headerTableColumns = headerTable.getData().getColumnsMap();
    Map<String, SeriesData> primaryTableColumns = primaryTable.getData().getColumnsMap();

    for (SeriesDefinition headerTableseriesDefinition : headerTableSeriesDefinitions) {
      Row headerRow = new Row();
      headerRow.setHeader(true);
      for (SeriesDefinition primaryTableSeriesDefinition : primaryTableSeriesDefinitions) {
        if (primaryTableSeriesDefinition.getIsDimension()) {
          headerRow.getCells().add(primaryTableSeriesDefinition.getDescription());
        }
      }

      String headerColumnId = headerTableseriesDefinition.getId();
      String nullFormat = headerTableseriesDefinition.getFormat().getNullFormat();
      for (int i = 0; i < headerRowCount; i++) {
        headerRow.getCells().add(SeriesDataHelper.getValueHelper(headerTableColumns.get(headerColumnId),
            headerTableseriesDefinition.getType(), i, nullFormat).toString());
      }
      table.getRows().add(headerRow);
    }

    // Construct the column data
    for (int i = 0; i < rowsCount; i++) {
      Row dataRow = new Row();
      for (SeriesDefinition primaryTableSeriesDefinition : primaryTableSeriesDefinitions) {
        String nullFormat = primaryTableSeriesDefinition.getFormat().getNullFormat();
        String primaryTableColumnId = primaryTableSeriesDefinition.getId();
        dataRow.getCells().add(SeriesDataHelper.getValueHelper(primaryTableColumns.get(primaryTableColumnId),
            primaryTableSeriesDefinition.getType(), i, nullFormat).toString());
      }
      table.getRows().add(dataRow);
    }

    Map<String, MetadataItem> metadata = primaryTable.getData().getMetadata().getItemsMap();
    for (Map.Entry<String,MetadataItem> entry : metadata.entrySet()) {
      table.getMetadata().put(entry.getValue().getName(), entry.getValue().getStringValue());
    }
    return table;
  }
  }