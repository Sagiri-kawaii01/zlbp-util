# 浙里办票发票数据企业接口工具类

使用时请在工具类头部填写

1. 企业纳税人识别号
2. 企业密码
3. 企业秘钥

## 运行速度
浙里办票的页数查询接口只能按天查询，下载接口按页下载，效率很低，所以工具类使用了线程池提高速率

测试下载一个月的发票（227张）：

单线程：30秒

多线程（工具类）：13秒

---

测试下载十个月的发票（1866张，2022-01-01~2022-11-11）：

多线程（工具类）：39秒


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
