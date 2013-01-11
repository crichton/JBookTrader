package com.jbooktrader.platform.performance;

import com.jbooktrader.platform.chart.*;
import com.jbooktrader.platform.commission.*;
import com.jbooktrader.platform.indicator.*;
import com.jbooktrader.platform.model.*;
import com.jbooktrader.platform.strategy.*;
import com.jbooktrader.platform.util.*;

import java.util.*;

/**
 * Performance manager evaluates trading strategy performance based on statistics
 * which include various factors, such as net profit, maximum draw down, profit factor, etc.
 */
public class PerformanceManager {
    private final int multiplier;
    private final Commission commission;
    private final Strategy strategy;

    private PerformanceChartData performanceChartData;
    private int trades, profitableTrades, previousPosition;
    private double tradeCommission, totalCommission;
    private double positionValue;
    private double totalBought, totalSold;
    private double tradeProfit, grossProfit, grossLoss, netProfit, netProfitAsOfPreviousTrade;
    private double peakNetProfit, maxDrawdown;
    private boolean isCompletedTrade;
    private double sumTradeProfit, sumTradeProfitSquared;
    //private double sumTradeProfit2, sumTradeProfitSquared2;
    //private int down;
    private long timeInMarketStart, timeInMarket;
    private long longTrades, shortTrades;
    //private List<Double> allTrades;


    public PerformanceManager(Strategy strategy, int multiplier, Commission commission) {
        this.strategy = strategy;
        this.multiplier = multiplier;
        this.commission = commission;
        //allTrades = new ArrayList<Double>();
    }

    public void createPerformanceChartData(BarSize barSize, List<Indicator> indicators) {
        performanceChartData = new PerformanceChartData(barSize, indicators);
    }

    public PerformanceChartData getPerformanceChartData() {
        return performanceChartData;
    }

    public int getTrades() {
        return trades;
    }

    public double getBias() {
        if (trades == 0) {
            return 0;
        }
        return 100 * (longTrades - shortTrades) / (double) trades;
    }


    public double getAveDuration() {
        if (trades == 0) {
            return 0;
        }
        // average number of minutes per trade
        return (double) timeInMarket / (trades * 1000 * 60);
    }


    public boolean getIsCompletedTrade() {
        return isCompletedTrade;
    }

    public double getPercentProfitableTrades() {
        return (trades == 0) ? 0 : (100.0d * profitableTrades / trades);
    }

    public double getAverageProfitPerTrade() {
        return (trades == 0) ? 0 : netProfit / trades;
    }

    public double getProfitFactor() {
        double profitFactor = 0;
        if (grossProfit > 0) {
            profitFactor = (grossLoss == 0) ? Double.POSITIVE_INFINITY : grossProfit / grossLoss;
        }
        return profitFactor;
    }

    public double getMaxDrawdown() {
        return maxDrawdown;
    }

    public double getTradeProfit() {
        return tradeProfit;
    }

    public Commission getCommission() {
        return commission;
    }

    public double getTradeCommission() {
        return tradeCommission;
    }

    public double getNetProfit() {
        return totalSold - totalBought + positionValue - totalCommission;
    }

    public double getKellyCriterion() {
        int unprofitableTrades = trades - profitableTrades;
        if (profitableTrades > 0) {
            if (unprofitableTrades > 0) {
                double aveProfit = grossProfit / profitableTrades;
                double aveLoss = grossLoss / unprofitableTrades;
                double winLossRatio = aveProfit / aveLoss;
                double probabilityOfWin = profitableTrades / (double) trades;
                double kellyCriterion = probabilityOfWin - (1 - probabilityOfWin) / winLossRatio;
                kellyCriterion *= 100;
                return kellyCriterion;
            }
            return 100;
        }
        return 0;
    }

    public double getCPI() {
        double cpi = getPerformanceIndex() * getProfitFactor() * getKellyCriterion() * getNetProfit();
        //if (getAveDuration() == 0) {
        //    return 0;
        //}
        //cpi /= Math.sqrt(getAveDuration()); //ekk
        cpi /= 100000;
        return cpi;
    }


    public double getPerformanceIndex() {

        double pi = 0;
        if (trades > 0) {
            double stDev = Math.sqrt(trades * sumTradeProfitSquared - sumTradeProfit * sumTradeProfit) / trades;
            pi = (stDev == 0) ? Double.POSITIVE_INFINITY : Math.sqrt(trades) * getAverageProfitPerTrade() / stDev;
        }

        return pi;
    }

    public void updatePositionValue(double price, int position) {
        positionValue = position * price * multiplier;
    }

    public void updateOnTrade(int quantity, double avgFillPrice, int position) {
        long snapshotTime = strategy.getMarketBook().getSnapshot().getTime();
        if (position != 0) {
            if (timeInMarketStart == 0) {
                timeInMarketStart = snapshotTime;
            }
        } else {
            timeInMarket += (snapshotTime - timeInMarketStart);
            timeInMarketStart = 0;
        }

        double tradeAmount = avgFillPrice * Math.abs(quantity) * multiplier;
        if (quantity > 0) {
            totalBought += tradeAmount;
        } else {
            totalSold += tradeAmount;
        }

        tradeCommission = commission.getCommission(Math.abs(quantity), avgFillPrice);
        totalCommission += tradeCommission;

        updatePositionValue(avgFillPrice, position);

        isCompletedTrade = (previousPosition > 0 && position < previousPosition);
        isCompletedTrade = isCompletedTrade || (previousPosition < 0 && position > previousPosition);

        if (isCompletedTrade) {
            trades++;
            if (previousPosition > 0) {
                longTrades++;
            } else if (previousPosition < 0) {
                shortTrades++;
            }


            netProfit = totalSold - totalBought + positionValue - totalCommission;
            peakNetProfit = Math.max(netProfit, peakNetProfit);
            maxDrawdown = Math.max(maxDrawdown, peakNetProfit - netProfit);

            tradeProfit = netProfit - netProfitAsOfPreviousTrade;
            netProfitAsOfPreviousTrade = netProfit;

            sumTradeProfit += tradeProfit;
            sumTradeProfitSquared += (tradeProfit * tradeProfit);

            //if (tradeProfit < 0) {
            //  sumTradeProfit2 += tradeProfit;
            //sumTradeProfitSquared2 += (tradeProfit * tradeProfit);
            //down++;
            //}


            if (tradeProfit >= 0) {
                profitableTrades++;
                grossProfit += tradeProfit;
            } else {
                grossLoss += (-tradeProfit);
            }

            //allTrades.add(tradeProfit);
        }


        if (Dispatcher.getInstance().getMode() == Mode.BackTest) {
            performanceChartData.update(new TimedValue(snapshotTime, netProfit));
        }

        previousPosition = position;
    }
}
