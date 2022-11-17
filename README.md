# 浙里办票发票数据企业接口工具类

使用时请在工具类头部填写

1. 企业纳税人识别号
2. 企业密码
3. 企业秘钥

## InvoiceData

InvoiceData类是用来导出Excel的，使用的是EasyExcel，参考[EasyExcel教程](https://cimoc.cn/2022/11/09/easyexcel/)

示例：
```java
// 下载到本地的ofd文件的目录
File file = new File("C:\\Users\\11047\\Desktop\\ofd");
// 获取所有ofd的路径
List<String> pathList = new ArrayList<>();
for (File listFile : file.listFiles()) {
    pathList.add(listFile.getPath());
}
// 将所有ofd转换为json
List<String> ofdToJson = ZlbpUtils.getOfdToJson(pathList);
// 将json数据转换为实体类
List<InvoiceData> data = new ArrayList<>();
for (String s : ofdToJson) {
    data.add(JSON.parseObject(s, InvoiceData.class));
}
// 输出目录
String filePath = "C:\\Users\\11047\\Desktop\\invoice_11.xlsx";
// EasyExcel写数据到excel文件
EasyExcel.write(filePath, InvoiceData.class).sheet().doWrite(data);
```
