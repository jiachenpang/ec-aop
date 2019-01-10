package stevenTest;

public class Chems {

    // 定义几个常量，分别为基础元素对应的原子质量
    final static int H = 1;
    final static int C = 12;
    final static int N = 14;
    final static int O = 16;
    final static double Cl = 35.5;
    final static int Mn = 55;
    final static double STEP = 0.001;

    public static void main(String[] str) {

        // 记录各主要元素对应的比例
        double c_percent = 23.53;
        double h_percent = 4.38;
        double n_percent = 5.62;
        double mn_percent = 16.63;

        // 各类物质对应的物质的量
        int C27H18O6 = C * 27 + H * 18 + O * 6;
        double MnCl2 = Mn + Cl * 2;
        int C3H7NO = C * 3 + H * 7 + N + O;
        int CH3OH = C + H * 3 + O + H;
        int H2O = H * 2 + O;

        // 用于记录结果
        double C27H18O6_percent, MnCl2_percent, C3H7NO_percent, CH3OH_percent, H2O_percent;
        C27H18O6_percent = MnCl2_percent = C3H7NO_percent = CH3OH_percent = H2O_percent = 0;

        // 由于Mn和N相对特殊，先从这两种物质进行下手,同第一版相比,Mn和N的比例也允许0.5的误差
        MnCl2_percent = mn_percent * MnCl2 / Mn;
        double MnCl2_percent_MAX = (mn_percent + 0.5) * MnCl2 / Mn;
        double MnCl2_percent_MIN = (mn_percent - 0.5) * MnCl2 / Mn;
        C3H7NO_percent = n_percent * C3H7NO / N;
        double C3H7NO_percent_MAX = (n_percent + 0.5) * C3H7NO / N;
        double C3H7NO_percent_MIN = (n_percent - 0.5) * C3H7NO / N;
        C27H18O6_percent = 0;
        double total_quality = 0;
        for (double i = MnCl2_percent_MIN; i < MnCl2_percent_MAX; i = i + 0.001) {
            for (double j = C3H7NO_percent_MIN; j < C3H7NO_percent_MAX; j = j + 0.001) {
                while (C27H18O6_percent < 100 - i - j) {
                    CH3OH_percent = 100 - i - j - C27H18O6_percent;
                    H2O_percent = 100 - i - j - C27H18O6_percent - CH3OH_percent;

                    // 物质总质量
                    total_quality = C27H18O6_percent * C27H18O6 + i * MnCl2 + j * C3H7NO
                            + CH3OH_percent * CH3OH + H2O_percent * H2O;

                    // 分别计算误差
                    if (Math.abs(Mn * i / total_quality - mn_percent) > 0.5) {
                        C27H18O6_percent = C27H18O6_percent + STEP;
                        continue;
                    }
                    if (Math.abs(7 * N * j / total_quality - n_percent) > 0.5) {
                        C27H18O6_percent = C27H18O6_percent + STEP;
                        continue;
                    }
                    if (Math.abs((C * 3 * j + C * 27 * C27H18O6_percent + C * CH3OH_percent) / total_quality
                            - c_percent) > 0.5) {
                        C27H18O6_percent = C27H18O6_percent + STEP;
                        continue;
                    }
                    if (Math.abs((H * C27H18O6_percent * 18 + H * j * 7 + H * 4 * CH3OH_percent + H * 2
                            * H2O_percent) / total_quality - h_percent) > 0.5) {
                        C27H18O6_percent = C27H18O6_percent + STEP;
                        continue;
                    }
                    System.out.println(Mn * i / total_quality - mn_percent);
                    System.out.println(7 * N * j / total_quality - n_percent);
                    System.out
                            .println((C * 3 * j + C * 27 * C27H18O6_percent) / total_quality - c_percent);
                    System.out.println((H * C27H18O6_percent * 18 + H * j * 7 + H * 4 * CH3OH_percent + H
                            * 2 * H2O_percent) / total_quality - h_percent);
                    break;
                }
            }
        }

        System.out.println(MnCl2_percent);
        System.out.println(C3H7NO_percent);
        System.out.println(C27H18O6_percent);
        System.out.println(CH3OH_percent);
        System.out.println(H2O_percent);
    }
}
