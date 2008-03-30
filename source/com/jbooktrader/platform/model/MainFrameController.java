package com.jbooktrader.platform.model;

import com.jbooktrader.platform.backtest.*;
import com.jbooktrader.platform.chart.*;
import com.jbooktrader.platform.dialog.*;
import com.jbooktrader.platform.marketdepth.*;
import com.jbooktrader.platform.optimizer.*;
import static com.jbooktrader.platform.preferences.JBTPreferences.*;
import com.jbooktrader.platform.preferences.*;
import com.jbooktrader.platform.strategy.*;
import com.jbooktrader.platform.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Acts as a controller in the Model-View-Controller pattern
 */
public class MainFrameController {
    private final MainFrameDialog mainViewDialog;
    private final JTable tradingTable;
    private final TradingTableModel tradingTableModel;
    private final PreferencesHolder prefs = PreferencesHolder.getInstance();

    public MainFrameController() throws JBookTraderException {
        mainViewDialog = new MainFrameDialog();
        Dispatcher.addListener(mainViewDialog);
        tradingTable = mainViewDialog.getTradingTable();
        tradingTableModel = mainViewDialog.getTradingTableModel();
        assignListeners();
    }

    private void exit() {
        prefs.set(MainWindowWidth, mainViewDialog.getSize().width);
        prefs.set(MainWindowHeight, mainViewDialog.getSize().height);
        prefs.set(MainWindowX, mainViewDialog.getX());
        prefs.set(MainWindowY, mainViewDialog.getY());
        Dispatcher.exit();
    }

    private Strategy getSelectedRowStrategy() throws JBookTraderException {
        int selectedRow = tradingTable.getSelectedRow();
        if (selectedRow < 0) {
            throw new JBookTraderException("No strategy is selected.");
        }
        return tradingTableModel.getStrategyForRow(selectedRow);
    }

    private Strategy createSelectedRowStrategy() throws JBookTraderException {
        int selectedRow = tradingTable.getSelectedRow();
        if (selectedRow < 0) {
            throw new JBookTraderException("No strategy is selected.");
        }
        return tradingTableModel.createStrategyForRow(selectedRow);
    }

    private void openURL(String url) {
        try {
            Browser.openURL(url);
        } catch (Throwable t) {
            Dispatcher.getReporter().report(t);
            MessageDialog.showError(mainViewDialog, t.getMessage());
        }
    }

    private void assignListeners() {

        tradingTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int modifiers = e.getModifiers();
                boolean actionRequested = (modifiers & InputEvent.BUTTON2_MASK) != 0;
                actionRequested = actionRequested || (modifiers & InputEvent.BUTTON3_MASK) != 0;
                if (actionRequested) {
                    int selectedRow = tradingTable.rowAtPoint(e.getPoint());
                    tradingTable.setRowSelectionInterval(selectedRow, selectedRow);
                    mainViewDialog.showPopup(e);
                    tradingTable.setRowSelectionInterval(selectedRow, selectedRow);
                }
            }
        });

        mainViewDialog.informationAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = getSelectedRowStrategy();
                    StrategyInformationDialog sid = new StrategyInformationDialog(mainViewDialog, strategy);
                    Dispatcher.addListener(sid);
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });


        mainViewDialog.backTestAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = createSelectedRowStrategy();
                    Dispatcher.setMode(Dispatcher.Mode.BackTest);
                    new BackTestDialog(mainViewDialog, strategy);
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        mainViewDialog.optimizeAction(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = getSelectedRowStrategy();
                    Dispatcher.setMode(Dispatcher.Mode.Optimization);
                    new OptimizerDialog(mainViewDialog, strategy.getName());
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        mainViewDialog.forwardTestAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = createSelectedRowStrategy();
                    Dispatcher.setMode(Dispatcher.Mode.ForwardTest);
                    new Thread(new StrategyRunner(strategy)).start();
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        mainViewDialog.tradeAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = createSelectedRowStrategy();
                    Dispatcher.setMode(Dispatcher.Mode.Trade);
                    new Thread(new StrategyRunner(strategy)).start();
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        mainViewDialog.saveMarketBookAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = getSelectedRowStrategy();
                    MarketBook book = strategy.getMarketBook();
                    if (book.size() != 0) {
                        BackTestFileWriter backTestFileWriter = new BackTestFileWriter(strategy);
                        backTestFileWriter.write(book);
                    } else {
                        String msg = "The book for this strategy is empty. Please run a strategy first.";
                        MessageDialog.showMessage(mainViewDialog, msg);
                    }
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.toString());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }

            }
        });

        mainViewDialog.chartAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Strategy strategy = getSelectedRowStrategy();
                    MarketBook book = strategy.getMarketBook();
                    if (book.size() != 0) {
                        StrategyPerformanceChart spChart = new StrategyPerformanceChart(strategy);
                        JFrame chartFrame = spChart.getChartFrame(mainViewDialog);
                        chartFrame.setVisible(true);
                    } else {
                        String msg = "There is no data to chart. Please run a strategy first.";
                        MessageDialog.showMessage(mainViewDialog, msg);
                    }
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        mainViewDialog.preferencesAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    new PreferencesDialog(mainViewDialog);
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                } finally {
                    mainViewDialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });


        mainViewDialog.discussionAction(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                openURL("http://groups.google.com/group/jbooktrader/topics?gvc=2");
            }
        });

        mainViewDialog.projectHomeAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openURL("http://code.google.com/p/jbooktrader/");
            }
        });

        mainViewDialog.exitAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exit();
                mainViewDialog.dispose();
            }
        });

        mainViewDialog.exitAction(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        mainViewDialog.aboutAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    new AboutDialog(mainViewDialog);
                } catch (Throwable t) {
                    MessageDialog.showError(mainViewDialog, t.getMessage());
                }
            }
        });
    }
}
