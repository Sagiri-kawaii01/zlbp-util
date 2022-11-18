package cn.cimoc.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.mychain.gov.sdk.GovDataSDK;
import com.alipay.mychain.gov.sdk.base.DecryptAndVerifyUtils;
import com.alipay.mychain.gov.sdk.base.FileResultZww;
import com.alipay.mychain.gov.sdk.base.GenericResult;
import com.alipay.mychain.gov.sdk.base.HttpClientUtils;
import com.alipay.mychain.gov.sdk.facade.enums.EncryptTpyeEnum;
import com.alipay.mychain.gov.sdk.request.SignedRequest;
import com.nuonuo.plate.extracte.BaseExtracte;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author LGZ
 * <p>
 */
public class ZlbpUtils {

    /**
     * 企业纳税⼈识别号
     */
    private static final String instCode = "";
    
    /**
     * 企业密码
     */
    private static final String password = "";
    
    /**
     * 企业密钥
     */
    private static final String privateKeyContent = "";


    private static final Logger logger = LoggerFactory.getLogger(ZlbpUtils.class);

    /**
     * 日期格式化规则，遵循接口规范
     */
    private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");


    /*
    线程池相关配置
    每一个请求都有自己的线程池
    由于浙里办票的接口只能按天请求数据，所以需要发很多次请求，利用线程可以提高效率
     */

    /**
     * 线程池核心大小
     * 一直存活的线程数量
     */
    private static final int CORE_POOL_SIZE = 16;

    /**
     * 线程池最大大小
     * 当线程池没有空闲线程，并且有新任务来时会创建新的线程
     */
    private static final int MAX_POOL_SIZE = 32;

    /**
     * 超过核心大小数量的线程的空闲存活时间
     */
    private static final long KEEP_ALIVE_TIME = 15;

    /**
     * 超过核心大小数量的线程的空闲存活时间单位
     */
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * 当线程池满时，新来的任务加入的等待队列
     * 浙里办票接口只能查询今年的数据
     */
    private static final int BLOCKING_QUEUE_CAPACITY = 366;

    /**
     * 默认线程超时时长
     */
    private static final long DEFAULT_TIMEOUT = 10;

    /**
     * 默认线程超时时长单位
     */
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MINUTES;

    /**
     * 页数接口
     */
    private static final String COUNT_URL = "http://zlbp.zhejiang.chinatax.gov.cn/api/zww/invoice/file/getInvoiceFilePackageCount";

    /**
     * 数据接口
     */
    private static final String INFO_URL = "http://zlbp.zhejiang.chinatax.gov.cn/api/zww/invoice/file/getInvoiceFilePackagesInfo";

    /**
     * 回写接口
     */
    private static final String BOOKING_URL = "http://zlbp.zhejiang.chinatax.gov.cn/api/invoice/booking";

    /**
     * 购销方标识——销方
     */
    public static final int GXFBS_XF = 0;

    /**
     * 购销方标识——购方
     */
    public static final int GXFBS_GF = 1;

    /**
     * 购销方标识——购销方
     */
    public static final int GXFBS_GXF = 2;


    /**
     * 发票类型
     */
    public enum FPLX {
        ZJINV_SPCL,
        ZJINV_SPE,
        ZJINV_ODN,
        ZJINV_ORD,
        ZJINV_ODN_R,
        ZJINV_TLF,
        ZJINV_USMV,
        ZJINV_UCSU,
        ZJGINV_GEN,
        ZJGINV_GMS,
        ZJGINV_PMSO,
        ZJGINV_GMSH,
        ZJGINV_SOGGMP,
        ZJGINV_RARGMP,
        ZJGINV_AOUGMP,
        ZJGINV_ROSGMP,
        ZJGINV_EBGMP,
        ZJGINV_TAXGMP,
        ZJGINV_ACQGMP,
        ZJGINV_HYDGMP,
        ZJGINV_GRAGMP
    }

    private static final Map<String, FPLX> fplxMap = new HashMap<String, FPLX>(32) {
        {
            put("增值税电子专用发票", FPLX.ZJINV_SPCL);
            put("增值税专用发票", FPLX.ZJINV_SPE);
            put("增值税普通发票", FPLX.ZJINV_ODN);
            put("增值税电子普通发票", FPLX.ZJINV_ORD);
            put("增值税普通发票(卷式)", FPLX.ZJINV_ODN_R);
            put("收费公路通行费增值税电子普通发票", FPLX.ZJINV_TLF);
            put("机动车销售统一发票", FPLX.ZJINV_USMV);
            put("二手车销售统一发票", FPLX.ZJINV_UCSU);
            put("通用（电子）发票", FPLX.ZJGINV_GEN);
            put("通用医疗服务电子发票", FPLX.ZJGINV_GMS);
            put("私立医疗服务（门诊）电子发票", FPLX.ZJGINV_PMSO);
            put("私立医疗服务（住院）电子发票", FPLX.ZJGINV_GMSH);
            put("货物销售通用机打发票", FPLX.ZJGINV_SOGGMP);
            put("修理修配通用机打发票", FPLX.ZJGINV_RARGMP);
            put("收购统一通用机打发票", FPLX.ZJGINV_AOUGMP);
            put("成品油销售通用机打发票", FPLX.ZJGINV_ROSGMP);
            put("出口业务通用机打发票", FPLX.ZJGINV_EBGMP);
            put("税务机关代开通用机打发票", FPLX.ZJGINV_TAXGMP);
            put("农产品收购通用机打发票", FPLX.ZJGINV_ACQGMP);
            put("水电业通用机打发票", FPLX.ZJGINV_HYDGMP);
            put("粮食收购通用机打发票", FPLX.ZJGINV_GRAGMP);
        }
    };

    public static FPLX getFplx(String fplx) {
        return fplxMap.get(fplx);
    }

    /**
     * 单张发票状态回写
     * @param fplx 发票类型
     * @param fpdm 发票代码
     * @param fphm 发票号码
     * @param kpyf 开票月份
     * @param xfsbh 发票销方识别号
     * @param gfsbh 发票购方识别号
     * @param km_1 科目一
     * @param km_2 科目二
     * @param fpzt 发票状态
     * @return 接口返回结果
     */
    public static GenericResult booking(FPLX fplx, String fpdm, String fphm, String kpyf, String xfsbh, String gfsbh, String km_1, String km_2, String fpzt) {
        String jsonStr = JSONObject.toJSONString(new LinkedHashMap<String, Object>(9) {
            {
                put("fplx", fplx);
                put("fpdm",fpdm);
                put("fphm", fphm);
                put("kpyf", kpyf);
                put("xfsbh", xfsbh);
                put("gfsbh", gfsbh);
                put("km_1", km_1);
                put("km_2", km_2);
                put("fpzt", fpzt);
            }
        });
        return post(sign(jsonStr), BOOKING_URL);
    }

    /**
     * 下载某天的发票OFD保存并解析为json（默认购销方）
     * @param dirPath 保存的目录
     * @param date 日期
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParseOnDate(String dirPath, String date) {
        return saveAndParseOnDate(dirPath, date, GXFBS_GXF);
    }

    /**
     * 下载某天的发票OFD保存并解析为json
     * @param dirPath 保存的目录
     * @param date 日期
     * @param gxfbs 购销方标识
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParseOnDate(String dirPath, String date, int gxfbs) {
        return saveAndParse(dirPath, date, 1, gxfbs);
    }

    /**
     * 下载某个月的发票OFD保存并解析为json（默认购销方）
     * @param dirPath 保存的目录
     * @param month 月份
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParseOnMonth(String dirPath, int month) {
        return saveAndParseOnMonth(dirPath, month, GXFBS_GXF);
    }

    /**
     * 下载某个月的发票OFD保存并解析为json
     * @param dirPath 保存的目录
     * @param month 月份
     * @param gxfbs 购销方标识
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParseOnMonth(String dirPath, int month, int gxfbs) {
        return saveAndParseOnMonth(dirPath, month, Calendar.getInstance().get(Calendar.YEAR), gxfbs);
    }

    /**
     * 下载某个月的发票OFD保存并解析为json
     * @param dirPath 保存的目录
     * @param month 月份
     * @param year 年份
     * @param gxfbs 购销方标识
     * @return 解析后的对象
     */
    private static OfdInfo saveAndParseOnMonth(String dirPath, int month, int year, int gxfbs) {
        SimpleCalendar calendar = newCalendar().setDate(year, month, 1);
        String date = calendar.getDate();
        int days = calendar.getDaysOfMonth();
        return saveAndParse(dirPath, date, days, gxfbs);
    }

    /**
     * 下载某段时间的发票OFD保存并解析为json（默认购销方）
     * @param dirPath 保存的目录
     * @param startAt 起始日期 e.g.2022-01-01
     * @param endAt 截止日期（包括）
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParse(String dirPath, String startAt, String endAt) {
        return saveAndParse(dirPath, startAt, getDaysBetween(startAt, endAt), GXFBS_GXF);
    }

    /**
     * 下载某段时间的发票OFD保存并解析为json
     * @param dirPath 保存的目录
     * @param startAt 起始日期 e.g.2022-01-01
     * @param endAt 截止日期（包括）
     * @param gxfbs 购销方标识
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParse(String dirPath, String startAt, String endAt, int gxfbs) {
        return saveAndParse(dirPath, startAt, getDaysBetween(startAt, endAt), gxfbs);
    }

    /**
     * 下载某段时间的发票OFD保存并解析为json（默认购销方）
     * @param dirPath 保存的目录
     * @param startAt 起始日期 e.g.2022-01-01
     * @param days 天数
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParse(String dirPath, String startAt, int days) {
        return saveAndParse(dirPath, startAt, days, GXFBS_GXF);
    }

    /**
     * 下载某段时间的发票OFD保存并解析为json
     * @param dirPath 保存的目录
     * @param startAt 起始时间 e.g.2022-01-01
     * @param days 天数
     * @param gxfbs 购销方标识
     * @return 解析后的对象
     */
    public static OfdInfo saveAndParse(String dirPath, String startAt, int days, int gxfbs) {
        File file = new File(dirPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        OfdInfo ofdInfo = new OfdInfo();
        List<String> pathList = new ArrayList<>();
        List<String> jsonList = new ArrayList<>();
        ofdInfo.setSavedPathList(pathList);
        ofdInfo.setJsonParsedList(jsonList);
        SimpleCalendar sc = newCalendar(startAt).doTask(days, (date) -> {
            logger.warn(date);
            String requestId = getRequestId();
            GenericResult countResult = packageCount(requestId, date, gxfbs);
            if (postSuccess(countResult)) {
                int page = JSON.parseObject(countResult.getData()).getInteger("totalPage");
                for (int i = 1; i <= page; i++) {
                    GenericResult infoResult = packageInfo(requestId, String.valueOf(i));
                    if (postSuccess(infoResult)) {
                        pathList.addAll(decryptList(infoResult.getData(), dirPath));
                    } else {
                        logger.warn("OFD下载失败：{}", infoResult.getErrorMsg());
                    }
                }
            } else {
                logger.warn("发票数量获取失败[{}]：{}", date, countResult.getErrorMsg());
            }
        });
        try {
            if (sc.awaitTermination()) {
                jsonList.addAll(getOfdToJson(pathList));
                return ofdInfo;
            } else {
                throw new ThreadInterruptedException("线程运行超时！");
            }
        } catch (InterruptedException e) {
            throw new ThreadInterruptedException("线程终止错误：" + e.getMessage());
        }

    }

    /**
     * 获取某天的发票数量（默认购销方）
     * @param date 日期 e.g.2022-01-01
     * @return 发票数量
     */
    public static int getCountOnDate(String date) {
        return getCountOnDate(date, GXFBS_GXF);
    }

    /**
     * 获取某天的发票数量
     * @param date 日期 e.g.2022-01-01
     * @param gxfbs 购销方标识
     * @return 发票数量
     */
    public static int getCountOnDate(String date, int gxfbs) {
        return getCount(date, 1, gxfbs);
    }

    /**
     * 获取某个月的发票数量（默认购销方）
     * @param month 月份
     * @return 发票数量
     */
    public static int getCountOnMonth(int month) {
        return getCountOnMonth(month, GXFBS_GXF);
    }

    /**
     * 获取某个月的发票数量
     * @param month 月份
     * @param gxfbs 购销方标识
     * @return 发票数量
     */
    public static int getCountOnMonth(int month, int gxfbs) {
        return getCountOnMonth(month, Calendar.getInstance().get(Calendar.YEAR), gxfbs);
    }

    /**
     * 获取某个月的发票数量
     * @param month 月份
     * @param year 年份
     * @param gxfbs 购销方标识
     * @return 发票数量
     */
    private static int getCountOnMonth(int month, int year, int gxfbs) {
        SimpleCalendar calendar = newCalendar().setDate(year, month, 1);
        String startAt = calendar.getDate();
        int days = calendar.getDaysOfMonth();
        return getCount(startAt, days, gxfbs);
    }

    /**
     * 获取一段时间的发票数量（默认购销方）
     * @param startAt 起始日期 e.g.2022-01-01
     * @param endAt 截止日期（包括）
     * @return 发票数量
     */
    public static int getCount(String startAt, String endAt) {
        return getCount(startAt, endAt, GXFBS_GXF);
    }

    /**
     * 获取一段时间的发票数量
     * @param startAt 起始日期 e.g.2022-01-01
     * @param endAt 截止日期（包括）
     * @param gxfbs 购销方标识
     * @return 发票数量
     */
    public static int getCount(String startAt, String endAt, int gxfbs) {
        return getCount(startAt, getDaysBetween(startAt, endAt), gxfbs);
    }

    /**
     * 获取一段时间的发票数量（默认购销方）
     * @param startAt 起始日期 e.g.2022-01-01
     * @param days 天数
     * @return 发票数量
     */
    public static int getCount(String startAt, int days) {
        return getCount(startAt, days, 2);
    }

    /**
     * 获取一段时间的发票数量
     * @param startAt 起始日期 e.g.2022-01-01
     * @param days 天数
     * @param gxfbs 购销方标识
     * @return 发票数量
     */
    public static int getCount(String startAt, int days, int gxfbs) {
        AtomicInteger cnt = new AtomicInteger(0);
        SimpleCalendar sc = newCalendar(startAt).doTask(days, (date) -> {
            GenericResult result = packageCount(getRequestId(), date, gxfbs);
            if (postSuccess(result)) {
                JSONObject data = JSON.parseObject(result.getData());
                cnt.addAndGet(data.getInteger("totalCount"));
            } else {
                logger.warn("发票数量获取失败[{}]：{}", date, result.getErrorMsg());
            }
        });
        try {
            if (sc.awaitTermination()) {
                return cnt.get();
            } else {
                throw new ThreadInterruptedException("线程运行超时！");
            }
        } catch (InterruptedException e) {
            throw new ThreadInterruptedException("线程终止错误：" + e.getMessage());
        }
    }

    /**
     * 获取两天之间的天数，包括截止日期
     * @param startAt 起始日期 e.g.2022-01-01
     * @param endAt 截止日期
     * @return 天数
     */
    public static int getDaysBetween(String startAt, String endAt) {
        try {
            Date day1 = format.parse(startAt);
            Date day2 = format.parse(endAt);
            long st = day1.getTime();
            long ed = day2.getTime();
            return (int) ((ed - st) / 24 / 60 / 60 / 1000) + 1;
        } catch (ParseException e) {
            throw new DateFormatException("日期解析错误，请检查参数：【开始时间】" + startAt + "，【结束时间】" + endAt);
        }
    }

    private static boolean postSuccess(GenericResult result) {
        return result.getSuccess() && "SUCCESS".equals(result.getErrorCode());
    }

    private static SimpleCalendar newCalendar() {
        return new SimpleCalendar();
    }

    private static SimpleCalendar newCalendar(String date) {
        return new SimpleCalendar(date);
    }

    public static List<String> getOfdToJson(List<String> pathList){
        List<String> jsonList = new ArrayList<>();
        try {
            for (String path : pathList) {
                File file = new File(path);
                if (file.getName().endsWith(".ofd")) {
                    InputStream is = Files.newInputStream(file.toPath());
                    BaseExtracte baseExtracte = new BaseExtracte();
                    JSONObject jsonObject = baseExtracte.readXbrl(is);
                    jsonList.add(jsonObject.toJSONString());
                    is.close();
                }
            }
        } catch (IOException e) {
            logger.error("OFD解析失败", e);
        }
        return jsonList;

    }

    public static List<String> decryptList(String dataList,String fileDirPathSrc) {
        List<FileResultZww> fileResultZwwList = JSON.parseArray(dataList, FileResultZww.class);
        List<String> filesPath = new ArrayList<>();
        fileResultZwwList.forEach(frz -> {
            try {
                DecryptAndVerifyUtils decryptAndVerifyUtils = new DecryptAndVerifyUtils(instCode, password, privateKeyContent);
                byte[] data = decryptAndVerifyUtils.decryptZww(frz);
                String filePath = fileDirPathSrc + "/" + frz.getFileName();
                DataOutputStream out = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)));
                for (byte datum : data) {
                    out.writeByte(datum);
                }
                out.flush();
                filesPath.add(filePath);
            } catch (IOException e) {
                logger.error(frz.getFileName() + "保存失败", e);
            }
        });
        return filesPath;
    }

    public static GenericResult packageInfo(String requestId, String pageNumber) {
        String jsonStr = JSONObject.toJSONString(new LinkedHashMap<String, Object>(3) {
            {
                put("nsrsbh", instCode);
                put("requestId",requestId);
                put("pageNumber", pageNumber);
            }
        });
        return post(sign(jsonStr), INFO_URL);
    }

    /**
     * 获取总页数
     * @param requestId 请求id
     * @param jgrq 加工日期
     * @param gxfbs 购销方标识
     */
    public static GenericResult packageCount(String requestId, String jgrq, int gxfbs) {
        String jsonStr = JSONObject.toJSONString(new LinkedHashMap<String, Object>(5) {
            {
                put("authorizedNsrsbh", instCode);
                put("gxfbs", gxfbs);
                put("jgrq", jgrq);
                put("nsrsbh", instCode);
                put("requestId", requestId);
            }
        });
        return post(sign(jsonStr), COUNT_URL);
    }

    private static String sign(String jsonStr) {
        GovDataSDK.getInstance().init(instCode, privateKeyContent, password, EncryptTpyeEnum.SM4);
        SignedRequest request = GovDataSDK.getInstance().getRequestBuilder().generateSignedPayload(jsonStr);
        return JSON.toJSONString(request);
    }

    /**
     * 获取页数和下载文件的请求必须有相同的requestId
     * 由于使用多线程下载，时间戳可能重复，所以使用UUID
     * @return 唯一的requestId
     */
    private static String getRequestId() {
        return UUID.randomUUID().toString();
    }

    private static GenericResult post(String jsonStr, String uri) {
        String response = null;
        try {
            response = HttpClientUtils.doPost(uri, jsonStr, ContentType.APPLICATION_JSON);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return JSON.parseObject(response, GenericResult.class);
    }

    private static class SimpleCalendar {

        private final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, new ArrayBlockingQueue<>(BLOCKING_QUEUE_CAPACITY));

        private final Calendar calendar;

        private final int[] month = {-1, Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST, Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER, Calendar.DECEMBER};

        private SimpleCalendar() {
            calendar = Calendar.getInstance();
        }

        private SimpleCalendar(int year, int month, int day) {
            this();
            setDate(year, month, day);
        }

        private SimpleCalendar(String date) {
            this();
            setDate(date);
        }

        private SimpleCalendar doTask(int days, Consumer<String> task) {
            for (int i = 0; i < days; i++) {
                String date = getDate();
                threadPool.execute(()-> task.accept(date));
                this.calendar.add(Calendar.DATE, 1);
            }
            threadPool.shutdown();
            return this;
        }

        private boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return threadPool.awaitTermination(timeout, unit);
        }

        private boolean awaitTermination() throws InterruptedException {
            return awaitTermination(DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
        }

        private int getMonth(int month) {
            if (month < 1 || month > 12) {
                throw new DateFormatException("月份不能小于1或者大于12");
            }
            return this.month[month];
        }

        private SimpleCalendar setDate(String date) {
            try {
                calendar.setTime(format.parse(date));
            } catch (ParseException e) {
                throw new DateFormatException("日期解析错误：" + e.getMessage());
            }
            return this;
        }

        private SimpleCalendar setDate(int year, int month, int day) {
            calendar.set(year, getMonth(month), day);
            return this;
        }

        private int getDaysOfMonth() {
            return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        }

        private String getDate() {
            return format.format(calendar.getTime());
        }
    }

    public static class OfdInfo {
        /**
         * 保存的文件路径
         */
        private List<String> savedPathList;

        /**
         * 解析后的json
         */
        private List<String> jsonParsedList;

        public List<String> getSavedPathList() {
            return savedPathList;
        }

        public void setSavedPathList(List<String> savedPathList) {
            this.savedPathList = savedPathList;
        }

        public List<String> getJsonParsedList() {
            return jsonParsedList;
        }

        public void setJsonParsedList(List<String> jsonParsedList) {
            this.jsonParsedList = jsonParsedList;
        }
    }

    public static class DateFormatException extends RuntimeException {
        public DateFormatException() {
            super();
        }

        public DateFormatException(String message) {
            super(message);
        }
    }

    public static class ThreadInterruptedException extends RuntimeException {
        public ThreadInterruptedException() {
        }

        public ThreadInterruptedException(String message) {
            super(message);
        }
    }
}
