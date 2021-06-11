/*
 * Copyright (C) 2021 kellypaulinc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlgranda.fede.controller;

import com.jlgranda.fede.SettingNames;
import com.jlgranda.fede.ejb.AccountService;
import com.jlgranda.fede.ejb.CashBoxDetailService;
import com.jlgranda.fede.ejb.CashBoxGeneralService;
import com.jlgranda.fede.ejb.CashBoxPartialService;
import com.jlgranda.fede.ejb.GeneralJournalService;
import com.jlgranda.fede.ejb.RecordDetailService;
import com.jlgranda.fede.ejb.RecordService;
import com.jlgranda.fede.ejb.sales.InvoiceService;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.beanutils.BeanUtils;
import org.jlgranda.fede.model.accounting.Account;
import org.jlgranda.fede.model.accounting.CashBoxPartial;
import org.jlgranda.fede.model.accounting.CashBoxDetail;
import org.jlgranda.fede.model.accounting.CashBoxGeneral;
import org.jlgranda.fede.model.accounting.GeneralJournal;
import org.jlgranda.fede.model.accounting.Record;
import org.jlgranda.fede.model.accounting.RecordDetail;
import org.jlgranda.fede.model.accounting.RecordDetail.RecordTDetailType;
import org.jlgranda.fede.model.document.DocumentType;
import org.jlgranda.fede.model.document.EmissionType;
import org.jpapi.model.CodeType;
import org.jpapi.model.Group;
import org.jpapi.model.Setting;
import org.jpapi.model.StatusType;
import org.jpapi.model.profile.Subject;
import org.jpapi.util.Dates;
import org.jpapi.util.I18nUtil;
import org.primefaces.event.RowEditEvent;
import org.primefaces.event.SelectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author kellypaulinc
 */
@Named
@ViewScoped
public class CashBoxHome extends FedeController implements Serializable {

    private static final long serialVersionUID = -1007161141552849702L;

    Logger logger = LoggerFactory.getLogger(CashBoxHome.class);

    @Inject
    private Subject subject;

    @Inject
    private OrganizationData organizationData;

    @Inject
    private SettingHome settingHome;

    @EJB
    private InvoiceService invoiceService;

    @EJB
    private AccountService accountService;

    @EJB
    private GeneralJournalService journalService;

    @EJB
    private RecordService recordService;

    @EJB
    private RecordDetailService recordDetailService;

    @EJB
    private CashBoxGeneralService cashBoxGeneralService;

    @EJB
    private CashBoxPartialService cashBoxPartialService;

    @EJB
    private CashBoxDetailService cashBoxDetailService;

    // Instancia de entidad <tt>CashBoxGeneral</tt> para edición manual
    private CashBoxGeneral cashBoxGeneral;

    //Instancia de entidad <tt>CashBox</tt> para edición manual
    private CashBoxPartial cashBoxPartial;
    // Instancia de entidad <tt>CashBoxDetail</tt> para edición manual

    private CashBoxDetail cashBoxDetail;

    //Calcular resumen
    private BigDecimal grossSalesTotal;
    private BigDecimal discountTotal;
    private BigDecimal salesTotal;
    private BigDecimal purchasesTotal;
    private BigDecimal costTotal;
    private BigDecimal profilTotal;
    private Long paxTotal;
    private List<Object[]> listDiscount;

    //Calcular el monto y panel de depósito
    private boolean activeButtonBreakdown;
    private boolean activePanelDeposit;
    private boolean activeSelectDeposit;
    private BigDecimal amountDeposit;
    private boolean activeButtonSelectDeposit;
    private Account selectedAccount;
    private Account depositAccount;

    //Calcular el resumen del cierre de caja
    private BigDecimal salesCash;
    private BigDecimal salesDedit;
    private BigDecimal salesCredit;
    private BigDecimal purchasesCash;
    private BigDecimal purchasesCredit;
    private BigDecimal purchasesCreditPaid;
    private BigDecimal transactionDebit;
    private BigDecimal transactionCredit;
    private BigDecimal transactionTotal;

    //Obtener saldo inicial del cierre de caja anterior
    private BigDecimal saldoInitial;

    //Calcular el dinero obtenido del cierre de caja
    private BigDecimal cashTotal;
    private BigDecimal saldoCash;

    //Desglosar el efectivo de Caja
    private List<CashBoxDetail> cashBoxBills;
    private List<CashBoxDetail> cashBoxMoneys;
    private boolean activePanelBreakdown;

    //Obtener lista de CashBoxClosed
    private List<CashBoxPartial> cashBoxsClosed;
    private List<CashBoxDetail> cashBoxsClosedBills;
    private List<CashBoxDetail> cashBoxsClosedMoneys;
    private CashBoxPartial cashBoxOpen;
    private boolean activeButtonCloseCash;

    //Calcular el dinero restante luego del depósito del Cierre de Caja
    private BigDecimal saldoCashFund;
    private boolean activePanelBreakdownFund;
    private List<CashBoxDetail> cashBoxsInitialBills;
    private List<CashBoxDetail> cashBoxsInitialMoneys;
    private List<CashBoxGeneral> cashBoxGeneralInitial;
    private CashBoxPartial cashBoxInitialFinish;
    private int activeIndex;
    private boolean panelVerification;

    @PostConstruct
    private void init() {
        int range = 0;
        try {
            range = Integer.valueOf(settingHome.getValue(SettingNames.DASHBOARD__SUMMARY_RANGE, "7"));
        } catch (java.lang.NumberFormatException nfe) {
            nfe.printStackTrace();
            range = 7;
        }
        //Inicialización de variables, objetos, métodos.
        setEnd(Dates.maximumDate(Dates.now()));
        setStart(Dates.minimumDate(getEnd()));
        setOutcome("cash-ajust");

        setCashBoxDetail(cashBoxDetailService.createInstance());
        setGrossSalesTotal(BigDecimal.ZERO);
        setDiscountTotal(BigDecimal.ZERO);
        setSalesTotal(BigDecimal.ZERO);
        setPurchasesTotal(BigDecimal.ZERO);
        setCostTotal(BigDecimal.ZERO);
        setProfilTotal(BigDecimal.ZERO);
        setPaxTotal(0L);

        setAmountDeposit(BigDecimal.ZERO);
        setActiveButtonSelectDeposit(true);
        setSelectedAccount(accountService.findUniqueByNamedQuery("Account.findByNameAndOrg", "CAJA DIA", this.organizationData.getOrganization()));
        setDepositAccount(accountService.findUniqueByNamedQuery("Account.findByNameAndOrg", "CAJA", this.organizationData.getOrganization()));

        setSalesCash(BigDecimal.ZERO);
        setSalesDedit(BigDecimal.ZERO);
        setSalesCredit(BigDecimal.ZERO);
        setPurchasesCash(BigDecimal.ZERO);
        setPurchasesCredit(BigDecimal.ZERO);
        setPurchasesCreditPaid(BigDecimal.ZERO);
        setTransactionDebit(BigDecimal.ZERO);
        setTransactionCredit(BigDecimal.ZERO);
        setTransactionTotal(BigDecimal.ZERO);
        setSaldoInitial(BigDecimal.ZERO);
        setCashTotal(BigDecimal.ZERO);
        setSaldoCash(BigDecimal.ZERO);
        setActiveIndex(0);

        calculeSummaryToday();
        findCashBoxs();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //GETTER AND SETTER
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public CashBoxGeneral getCashBoxGeneral() {
        return cashBoxGeneral;
    }

    public void setCashBoxGeneral(CashBoxGeneral cashBoxGeneral) {
        this.cashBoxGeneral = cashBoxGeneral;
    }

    public CashBoxPartial getCashBoxPartial() {
        return cashBoxPartial;
    }

    public void setCashBoxPartial(CashBoxPartial cashBoxPartial) {
        this.cashBoxPartial = cashBoxPartial;
    }

    public CashBoxDetail getCashBoxDetail() {
        return cashBoxDetail;
    }

    public void setCashBoxDetail(CashBoxDetail cashBoxDetail) {
        this.cashBoxDetail = cashBoxDetail;
    }

    public BigDecimal getGrossSalesTotal() {
        return grossSalesTotal;
    }

    public void setGrossSalesTotal(BigDecimal grossSalesTotal) {
        this.grossSalesTotal = grossSalesTotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public void setDiscountTotal(BigDecimal discountTotal) {
        this.discountTotal = discountTotal;
    }

    public BigDecimal getSalesTotal() {
        return salesTotal;
    }

    public void setSalesTotal(BigDecimal salesTotal) {
        this.salesTotal = salesTotal;
    }

    public BigDecimal getPurchasesTotal() {
        return purchasesTotal;
    }

    public void setPurchasesTotal(BigDecimal purchasesTotal) {
        this.purchasesTotal = purchasesTotal;
    }

    public BigDecimal getCostTotal() {
        return costTotal;
    }

    public void setCostTotal(BigDecimal costTotal) {
        this.costTotal = costTotal;
    }

    public BigDecimal getProfilTotal() {
        return profilTotal;
    }

    public void setProfilTotal(BigDecimal profilTotal) {
        this.profilTotal = profilTotal;
    }

    public Long getPaxTotal() {
        return paxTotal;
    }

    public void setPaxTotal(Long paxTotal) {
        this.paxTotal = paxTotal;
    }

    public List<Object[]> getListDiscount() {
        return listDiscount;
    }

    public void setListDiscount(List<Object[]> listDiscount) {
        this.listDiscount = listDiscount;
    }

    public BigDecimal getAmountDeposit() {
        return amountDeposit;
    }

    public void setAmountDeposit(BigDecimal amountDeposit) {
        this.amountDeposit = amountDeposit;
    }

    public Account getSelectedAccount() {
        return selectedAccount;
    }

    public void setSelectedAccount(Account selectedAccount) {
        this.selectedAccount = selectedAccount;
    }

    public Account getDepositAccount() {
        return depositAccount;
    }

    public void setDepositAccount(Account depositAccount) {
        this.depositAccount = depositAccount;
    }

    public BigDecimal getSalesCash() {
        return salesCash;
    }

    public void setSalesCash(BigDecimal salesCash) {
        this.salesCash = salesCash;
    }

    public BigDecimal getSalesDedit() {
        return salesDedit;
    }

    public void setSalesDedit(BigDecimal salesDedit) {
        this.salesDedit = salesDedit;
    }

    public BigDecimal getSalesCredit() {
        return salesCredit;
    }

    public void setSalesCredit(BigDecimal salesCredit) {
        this.salesCredit = salesCredit;
    }

    public BigDecimal getPurchasesCash() {
        return purchasesCash;
    }

    public void setPurchasesCash(BigDecimal purchasesCash) {
        this.purchasesCash = purchasesCash;
    }

    public BigDecimal getPurchasesCredit() {
        return purchasesCredit;
    }

    public void setPurchasesCredit(BigDecimal purchasesCredit) {
        this.purchasesCredit = purchasesCredit;
    }

    public BigDecimal getPurchasesCreditPaid() {
        return purchasesCreditPaid;
    }

    public void setPurchasesCreditPaid(BigDecimal purchasesCredit) {
        this.purchasesCreditPaid = purchasesCredit;
    }

    public BigDecimal getTransactionDebit() {
        return transactionDebit;
    }

    public void setTransactionDebit(BigDecimal transactionDebit) {
        this.transactionDebit = transactionDebit;
    }

    public BigDecimal getTransactionCredit() {
        return transactionCredit;
    }

    public void setTransactionCredit(BigDecimal transactionCredit) {
        this.transactionCredit = transactionCredit;
    }

    public BigDecimal getTransactionTotal() {
        return transactionTotal;
    }

    public void setTransactionTotal(BigDecimal transactionTotal) {
        this.transactionTotal = transactionTotal;
    }

    public BigDecimal getSaldoInitial() {
        return saldoInitial;
    }

    public void setSaldoInitial(BigDecimal saldoInitial) {
        this.saldoInitial = saldoInitial;
    }

    public BigDecimal getCashTotal() {
        return cashTotal;
    }

    public void setCashTotal(BigDecimal cashTotal) {
        this.cashTotal = cashTotal;
    }

    public BigDecimal getSaldoCash() {
        return saldoCash;
    }

    public void setSaldoCash(BigDecimal saldoCash) {
        this.saldoCash = saldoCash;
    }

    public BigDecimal getSaldoCashFund() {
        return saldoCashFund;
    }

    public void setSaldoCashFund(BigDecimal saldoCashFund) {
        this.saldoCashFund = saldoCashFund;
    }

    public List<CashBoxDetail> getCashBoxBills() {
        return cashBoxBills;
    }

    public void setCashBoxBills(List<CashBoxDetail> cashBoxBills) {
        this.cashBoxBills = cashBoxBills;
    }

    public List<CashBoxDetail> getCashBoxMoneys() {
        return cashBoxMoneys;
    }

    public void setCashBoxMoneys(List<CashBoxDetail> cashBoxMoneys) {
        this.cashBoxMoneys = cashBoxMoneys;
    }

    public CashBoxPartial getCashBoxOpen() {
        if (cashBoxGeneral.getId() != null) {
            List<CashBoxPartial> cashBoxOpens = cashBoxPartialService.findByNamedQueryWithLimit("CashBoxPartial.findByCashBoxGeneralAndStatus", 1, this.cashBoxGeneral, CashBoxPartial.Status.OPEN);
            if (!cashBoxOpens.isEmpty()) {
                cashBoxOpen = cashBoxOpens.get(0);
            }
        }
        return cashBoxOpen;
    }

    public void setCashBoxOpen(CashBoxPartial cashBoxOpen) {
        this.cashBoxOpen = cashBoxOpen;
    }

    public List<CashBoxPartial> getCashBoxsClosed() {
        if (cashBoxGeneral.getId() != null) {
            cashBoxsClosed = cashBoxPartialService.findByNamedQuery("CashBoxPartial.findByCashBoxGeneralAndStatusAndStatusPriority", this.cashBoxGeneral, CashBoxPartial.Status.CLOSED, CashBoxPartial.StatusPriority.INTERMEDIATE);
        }
        return cashBoxsClosed;
    }

    public void setCashBoxsClosed(List<CashBoxPartial> cashBoxsClosed) {
        this.cashBoxsClosed = cashBoxsClosed;
    }

    public List<CashBoxGeneral> getCashBoxGeneralInitial() {
        return cashBoxGeneralInitial = cashBoxGeneralService.findByNamedQueryWithLimit("CashBoxGeneral.findCashBoxGeneralByLastCreatedOnAndOrg", 1, this.organizationData.getOrganization(), Dates.minimumDate(Dates.now()));
    }

    public void setCashBoxGeneralInitial(List<CashBoxGeneral> cashBoxGeneralInitial) {
        this.cashBoxGeneralInitial = cashBoxGeneralInitial;
    }

    public CashBoxPartial getCashBoxInitialFinish() {
        if (!getCashBoxGeneralInitial().isEmpty()) {
            cashBoxInitialFinish = cashBoxPartialService.findUniqueByNamedQuery("CashBoxPartial.findByCashBoxGeneralAndStatusAndStatusPriority", this.cashBoxGeneralInitial.get(0), CashBoxPartial.Status.CLOSED, CashBoxPartial.StatusPriority.FINAL);
            if (this.cashBoxInitialFinish.getVerification_TotalBreakdown() != null) {
                if (this.cashBoxGeneral.getId() != null) {
                    cashBoxInitialFinish = cashBoxPartialService.findUniqueByNamedQuery("CashBoxPartial.findByCashBoxGeneralAndStatusAndStatusPriority", this.cashBoxGeneral, CashBoxPartial.Status.CLOSED, CashBoxPartial.StatusPriority.FINAL);
                }
            }
        } else {
            if (this.cashBoxGeneral.getId() != null) {
                cashBoxInitialFinish = cashBoxPartialService.findUniqueByNamedQuery("CashBoxPartial.findByCashBoxGeneralAndStatusAndStatusPriority", this.cashBoxGeneral, CashBoxPartial.Status.CLOSED, CashBoxPartial.StatusPriority.FINAL);
            }
        }
        return cashBoxInitialFinish;
    }

    public void setCashBoxInitialFinish(CashBoxPartial cashBoxInitialFinish) {
        this.cashBoxInitialFinish = cashBoxInitialFinish;
    }

    public List<CashBoxDetail> cashBoxsClosedBills(CashBoxPartial cashBoxPartial) {
        cashBoxsClosedBills = new ArrayList<>();
        cashBoxPartial.getCashBoxDetails().stream().filter(cbDetail -> (cbDetail.getDenomination_type().equals(CashBoxDetail.DenominationType.BILL))).forEachOrdered(cbDetail -> {
            cashBoxsClosedBills.add(cbDetail);
        });
        return cashBoxsClosedBills;
    }

    public List<CashBoxDetail> cashBoxsClosedMoneys(CashBoxPartial cashBoxPartial) {
        cashBoxsClosedMoneys = new ArrayList<>();
        cashBoxPartial.getCashBoxDetails().stream().filter(cbDetail -> (cbDetail.getDenomination_type().equals(CashBoxDetail.DenominationType.MONEY))).forEachOrdered(cbDetail -> {
            cashBoxsClosedMoneys.add(cbDetail);
        });
        return cashBoxsClosedMoneys;
    }

    public List<CashBoxDetail> cashBoxsInitialBills(CashBoxPartial cashBoxPartial) {
        cashBoxsInitialBills = new ArrayList<>();
        cashBoxPartial.getCashBoxDetails().stream().filter(cbDetail -> (cbDetail.getDenomination_type().equals(CashBoxDetail.DenominationType.BILL))).forEachOrdered(cbDetail -> {
            cashBoxsInitialBills.add(cbDetail);
        });
        return cashBoxsInitialBills;
    }

    public List<CashBoxDetail> cashBoxsInitialMoneys(CashBoxPartial cashBoxPartial) {
        cashBoxsInitialMoneys = new ArrayList<>();
        cashBoxPartial.getCashBoxDetails().stream().filter(cbDetail -> (cbDetail.getDenomination_type().equals(CashBoxDetail.DenominationType.MONEY))).forEachOrdered(cbDetail -> {
            cashBoxsInitialMoneys.add(cbDetail);
        });
        return cashBoxsInitialMoneys;
    }

    public boolean existBreakdownSecondary() {
        if (this.cashBoxGeneral.getId() != null) {
            return !cashBoxPartialService.findByNamedQuery("CashBoxPartial.findByCashBoxGeneralAndOwnerAndPriorityOrder", this.cashBoxGeneral, this.subject, CashBoxPartial.Priority.SECONDARY).isEmpty();
        } else {
            return false;
        }
    }

    public boolean isActiveButtonBreakdown() {
        if (this.cashBoxGeneral.getId() != null && this.cashBoxPartial.getId() != null) {
            if (this.cashBoxPartial.getStatusCashBoxPartial().equals(CashBoxPartial.Status.OPEN)) {
                activeButtonBreakdown = true; //Deshabilitar el botón
                setActivePanelBreakdown(true); //Mostrar el panel de desglose del CashBoxPartial Abierto
                setActivePanelDeposit(false); //Ocultar el panel de depósito
            } else {
                setActivePanelBreakdown(false); //Ocultar el panel de desglose
                if (existBreakdownSecondary() == true || getCashBoxOpen() != null) {
                    setActivePanelDeposit(false);
                    activeButtonBreakdown = true;
                }else{
                    setActivePanelDeposit(true);
                }
            }
        } else {
            if (this.cashBoxGeneral.getId() != null) {
                if (CashBoxGeneral.Status.CLOSED.equals(this.cashBoxGeneral.getStatusCashBoxGeneral())) {
                    setActivePanelDeposit(false);
                    activeButtonBreakdown = true;
                }
            } else {
                if (getCashBoxOpen() != null) {
                    setActivePanelDeposit(false);
                    activeButtonBreakdown = true;
                }
            }
        }
        return activeButtonBreakdown;
    }

    public boolean isActivePanelBreakdown() {
        chargeListCashBoxBillMoney(); //Cargar por defecto el detalle del CashBoxPartial en las tablas de la vista
        return activePanelBreakdown;
    }

    public void setActivePanelBreakdown(boolean activePanelBreakdown) {
        this.activePanelBreakdown = activePanelBreakdown;
    }

    public void setActiveButtonBreakdown(boolean activeButtonBreakdown) {
        this.activeButtonBreakdown = activeButtonBreakdown;
    }

    public boolean isActivePanelDeposit() {
//        if (this.cashBoxGeneral.getId() != null && existBreakdownSecondary() == false) {
//            if (this.cashBoxPartial.getId() == null) {
//                activePanelDeposit = true;
//            } else {
//                if (this.cashBoxPartial.getStatusCashBoxPartial().equals(CashBoxPartial.Status.CLOSED)) {
//                    return true;
//                }
//            }
//        }
        return activePanelDeposit;
    }

    public void setActivePanelDeposit(boolean activePanelDeposit) {
        this.activePanelDeposit = activePanelDeposit;
    }

    public boolean isActiveSelectDeposit() {
        return activeSelectDeposit;
    }

    public void setActiveSelectDeposit(boolean activeSelectDeposit) {
        this.activeSelectDeposit = activeSelectDeposit;
    }

    public boolean isActiveButtonSelectDeposit() {
        return activeButtonSelectDeposit;
    }

    public void setActiveButtonSelectDeposit(boolean activeButtonSelectDeposit) {
        this.activeButtonSelectDeposit = activeButtonSelectDeposit;
    }

    public boolean isActiveButtonCloseCash() {
        if (cashBoxOpen != null || this.cashBoxGeneral.getId() == null) {
            activeButtonCloseCash = true; //Deshabilitar Botón Cierre de Caja General, porque existe un Cierre de Caja Parcial abierto
        } else {
            if (cashBoxGeneral.getId() != null && cashBoxGeneral.getStatusCashBoxGeneral().equals(CashBoxGeneral.Status.OPEN)) {
                activeButtonCloseCash = false;
            } else {
                activeButtonCloseCash = true;
            }
        }
        return activeButtonCloseCash;
    }

    public void setActiveButtonCloseCash(boolean activeButtonCloseCash) {
        this.activeButtonCloseCash = activeButtonCloseCash;
    }

    public boolean isActivePanelBreakdownFund() {
        return activePanelBreakdownFund;
    }

    public void setActivePanelBreakdownFund(boolean activePanelBreakdownFund) {
        this.activePanelBreakdownFund = activePanelBreakdownFund;
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void setActiveIndex(int activeIndex) {
        this.activeIndex = activeIndex;
    }

    public boolean isPanelVerification() {
        if (getCashBoxInitialFinish() != null) {
            if (this.cashBoxPartial.getId() == null) {
                if (this.cashBoxGeneral.getId() != null) {
                    if (this.cashBoxGeneral.getStatusCashBoxGeneral().equals(CashBoxGeneral.Status.OPEN)) {
                        panelVerification = true;
                    }
                }
            } else {
                if (this.cashBoxGeneral.getStatusCashBoxGeneral().equals(CashBoxGeneral.Status.OPEN)) {
                    panelVerification = true;
                } else if (this.cashBoxInitialFinish.getCashBoxGeneral().getId().equals(this.cashBoxPartial.getCashBoxGeneral().getId())
                        && !this.cashBoxInitialFinish.getOwner().equals(this.cashBoxPartial.getOwner())) {
                    panelVerification = true;
                }
            }
        }
        return panelVerification;
    }

    public void setPanelVerification(boolean panelVerification) {
        this.panelVerification = panelVerification;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // MÉTODOS/FUNCIONES
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //VENTAS/COMPRAS: SUMMARY
    public void calculeSummaryToday() {
        calculeSummary(getStart(), getEnd());
        setListDiscount(getListDiscount(getStart(), getEnd()));
        calculeSummaryCash(getStart(), getEnd());
    }

    private void calculeSummary(Date _start, Date _end) {
        this.costTotal = BigDecimal.ZERO;
        List<Object[]> objects = invoiceService.findObjectsByNamedQueryWithLimit("Invoice.findTotalInvoiceSalesDiscountBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), DocumentType.INVOICE, StatusType.CLOSE.toString(), _start, _end);
        objects.stream().forEach((Object[] object) -> {
            this.grossSalesTotal = (BigDecimal) object[0];
            this.discountTotal = (BigDecimal) object[1];
            this.salesTotal = (BigDecimal) object[2];
        });
        if (this.grossSalesTotal == null) {
            this.grossSalesTotal = BigDecimal.ZERO;
        }
        if (this.discountTotal == null) {
            this.discountTotal = BigDecimal.ZERO;
        }
        if (this.salesTotal == null) {
            this.salesTotal = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("FacturaElectronica.findTotalByEmissionTypeBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), _start, _end, EmissionType.PURCHASE_CASH);
        objects.stream().forEach((Object object) -> {
            this.purchasesTotal = (BigDecimal) object;
        });
        if (this.purchasesTotal == null) {
            this.purchasesTotal = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("Invoice.findTotalInvoiceSalesPaxBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), DocumentType.INVOICE, StatusType.CLOSE.toString(), _start, _end);
        objects.stream().forEach((Object object) -> {
            this.paxTotal = (Long) object;
        });
        if (this.paxTotal == null) {
            this.paxTotal = 0L;
        }
        this.profilTotal = this.salesTotal.subtract(this.purchasesTotal.add(this.costTotal));
    }

    public List<Object[]> getListDiscount(Date _start, Date _end) {
        List<Object[]> objects = invoiceService.findObjectsByNamedQueryWithLimit("Invoice.findTotalInvoiceBussinesSalesDiscountBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), DocumentType.INVOICE, StatusType.CLOSE.toString(), _start, _end, BigDecimal.ZERO);
        return objects;
    }

    public BigDecimal getListDiscountTotal() {
        BigDecimal total = new BigDecimal(0);
        for (int i = 0; i < getListDiscount().size(); i++) {
            total = total.add((BigDecimal) getListDiscount().get(i)[4]);
        }
        return total;
    }

    //AJUSTE DE CIERRE DE CAJA
    private void calculeSummaryCash(Date _start, Date _end) {
        if (!getCashBoxGeneralInitial().isEmpty()) {
            this.saldoInitial = this.cashBoxGeneralInitial.get(0).getTotalBreakdownFinal();
        }
        if (this.saldoInitial == null) {
            this.saldoInitial = BigDecimal.ZERO;
        }
        List<Object[]> objects = invoiceService.findObjectsByNamedQueryWithLimit("Invoice.findTotalInvoiceSalesMethodBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), DocumentType.INVOICE, StatusType.CLOSE.toString(), _start, _end, "EFECTIVO");
        objects.stream().forEach((Object object) -> {
            this.salesCash = (BigDecimal) object;
        });
        if (this.salesCash == null) {
            this.salesCash = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("Invoice.findTotalInvoiceSalesMethodBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), DocumentType.INVOICE, StatusType.CLOSE.toString(), _start, _end, "TARJETA DEBITO");
        objects.stream().forEach((Object object) -> {
            this.salesDedit = (BigDecimal) object;
        });
        if (this.salesDedit == null) {
            this.salesDedit = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("Invoice.findTotalInvoiceSalesMethodBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), DocumentType.INVOICE, StatusType.CLOSE.toString(), _start, _end, "TARJETA CREDITO");
        objects.stream().forEach((Object object) -> {
            this.salesCredit = (BigDecimal) object;
        });
        if (this.salesCredit == null) {
            this.salesCredit = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("FacturaElectronica.findTotalByEmissionTypeBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), _start, _end, EmissionType.PURCHASE_CASH);
        objects.stream().forEach((Object object) -> {
            this.purchasesCash = (BigDecimal) object;
        });
        if (this.purchasesCash == null) {
            this.purchasesCash = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("FacturaElectronica.findTotalByEmissionTypeBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), _start, _end, EmissionType.PURCHASE_CREDIT);
        objects.stream().forEach((Object object) -> {
            this.purchasesCredit = (BigDecimal) object;
        });
        if (this.purchasesCredit == null) {
            this.purchasesCredit = BigDecimal.ZERO;
        }
        objects = invoiceService.findObjectsByNamedQueryWithLimit("FacturaElectronica.findTotalByEmissionTypePayBetweenOrg", Integer.MAX_VALUE, this.organizationData.getOrganization(), _start, _end, EmissionType.PURCHASE_CREDIT);
        objects.stream().forEach((Object object) -> {
            this.purchasesCreditPaid = (BigDecimal) object;
        });
        if (this.purchasesCreditPaid == null) {
            this.purchasesCreditPaid = BigDecimal.ZERO;
        }
        objects = recordDetailService.findObjectsByNamedQueryWithLimit("RecordDetail.findTotalByAccountAndType", Integer.MAX_VALUE, this.selectedAccount, RecordTDetailType.DEBE, _start, _end, this.organizationData.getOrganization());
        objects.stream().forEach((Object object) -> {
            this.transactionDebit = (BigDecimal) object;
        });
        if (this.transactionDebit == null) {
            this.transactionDebit = BigDecimal.ZERO;
        }
        objects = recordDetailService.findObjectsByNamedQueryWithLimit("RecordDetail.findTotalByAccountAndType", Integer.MAX_VALUE, this.selectedAccount, RecordTDetailType.HABER, _start, _end, this.organizationData.getOrganization());
        objects.stream().forEach((Object object) -> {
            this.transactionCredit = (BigDecimal) object;
        });
        if (this.transactionCredit == null) {
            this.transactionCredit = BigDecimal.ZERO;
        }

        this.salesTotal = this.salesCash.add(this.salesDedit).add(this.salesCredit); //Suma de ventas en caja
        this.purchasesTotal = this.purchasesCash.add(this.purchasesCredit);//Suma de compras en caja
        this.transactionTotal = this.transactionDebit.subtract(this.transactionCredit);//Suma de transacciones en caja
        this.cashTotal = this.salesTotal.subtract(this.purchasesTotal).add(this.transactionTotal); //Valor de Caja según los Libros: Total Ventas - Total Compras y la + o - de Transacciones
        // this.saldoCash = this.cashTotal.subtract(this.salesDedit.add(this.salesCredit)); //Dinero en efectivo de Caja
        this.saldoCash = this.salesCash.subtract(this.purchasesCash.add(this.purchasesCreditPaid)).add(this.transactionTotal); //Dinero en efectivo de Caja
        this.saldoCash = this.saldoCash.add(this.saldoInitial); //Aumentar el Dinero que quedó del cierre de caja anterior
    }

    public void findCashBoxs() { //Buscar CashBoxGeneral y CashBox del Cajero en caso de existencia
        this.cashBoxGeneral = cashBoxGeneralService.findUniqueByNamedQuery("CashBoxGeneral.findByCreatedOnAndOrg", getStart(), getEnd(), this.organizationData.getOrganization());
        if (this.cashBoxGeneral == null) {
            this.cashBoxGeneral = cashBoxGeneralService.createInstance();
        } else {
            List<CashBoxPartial> partialExit = cashBoxPartialService.findByNamedQueryWithLimit("CashBoxPartial.findByCashBoxGeneralAndOwner", 1, this.cashBoxGeneral, this.subject);
            if (!partialExit.isEmpty()) {
                this.cashBoxPartial = partialExit.get(0);
            }
            this.saldoCashFund = this.cashBoxGeneral.getTotalBreakdownFinal(); //Saldo registrado según el último cierre de caja
        }
        if (this.cashBoxPartial == null) {
            this.cashBoxPartial = cashBoxPartialService.createInstance();
        }
        isActiveButtonBreakdown(); //Verificar estado del botón de Desglose
        if (this.saldoCash.compareTo(BigDecimal.ZERO) == 0 || this.saldoCash.compareTo(BigDecimal.ZERO) == -1) {
            setActiveButtonBreakdown(true);
        }
        System.out.println("this.cashBoxGeneral" + this.cashBoxGeneral);
        System.out.println("this.cashBoxPartial" + this.cashBoxPartial);
        System.out.println("<-------------------------->");
//        
//        else {
//            if (this.cashBoxPartial.getStatusCashBoxPartial().equals(CashBoxPartial.Status.OPEN)) {
//                setActiveIndex(-1);
//                this.activeButtonBreakdown = false; //Deshabilitar el botón si ya se existe el CashBox, y sólo se va a editar
//            } else {
//                if (cashBoxPartialService.findByNamedQuery("CashBoxPartial.findByCashBoxGeneralAndOwnerAndPriorityOrder", this.cashBoxGeneral, this.subject, CashBoxPartial.Priority.SECONDARY).isEmpty()) {
//                    activeButtonBreakdown = true; //Deshabilitar el botón de desglose
//                }
//            }
//            this.activePanelBreakdown = this.cashBoxPartial.getStatusCashBoxPartial().equals(CashBoxPartial.Status.OPEN); //Mostrar/Ocultar el Panel de Detalle según estado del CashBox
//            chargeListCashBoxBillMoney();
//
//            this.saldoCashFund = this.cashBoxGeneral.getTotalBreakdownFinal(); //Saldo registrado según el último cierre de caja
//            if (this.cashBoxPartial.getStatusCashBoxPartial().equals(CashBoxPartial.Status.CLOSED)) {
//                this.activePanelDeposit = true;
//            }
//        }
//        if (this.cashBoxGeneral.getId() != null) {
//            if (this.cashBoxGeneral.getStatusCashBoxGeneral().equals(CashBoxGeneral.Status.CLOSED)) {
//                this.activePanelDeposit = false;
//            }
//        }
//
//        if (getCashBoxOpen() == null) {
//            this.activeButtonBreakdown = this.saldoCash.compareTo(BigDecimal.ZERO) == 1; //Habilitar o deshabilitar el botón de desgloce según el saldoCash > 0
//        } else {
//            this.activeButtonBreakdown = true;
//        }
//        this.activeSelectMenuBill = false; //Habilitar los Selects del Panel de Detalle para Billetes y Monedas
//        this.activeSelectMenuMoney = false;
//        System.out.println("this.cashBoxGeneral" + this.cashBoxGeneral);
//        System.out.println("this.cashBoxPartial" + this.cashBoxPartial);
//        System.out.println("<-------------------------->");
    }

    private void generateCashBoxPartialDetails() {
        List<Setting> denominations = settingHome.findByCodeType(CodeType.DENOMINATION);
        if (this.cashBoxPartial.getCashBoxDetails().isEmpty()) {
            for (Setting d : denominations) {
                this.cashBoxDetail = cashBoxDetailService.createInstance(); //Preparar para un nuevo detalle del CashBox Instanciado
                if (d.getCategory().equals(CashBoxDetail.DenominationType.BILL.toString())) {
                    this.cashBoxDetail.setDenomination_type(CashBoxDetail.DenominationType.BILL);
                } else if (d.getCategory().equals(CashBoxDetail.DenominationType.MONEY.toString())) {
                    this.cashBoxDetail.setDenomination_type(CashBoxDetail.DenominationType.MONEY);
                }
                this.cashBoxDetail.setDenomination(d.getLabel());
                this.cashBoxDetail.setQuantity(0L);
                this.cashBoxDetail.setValuer(new BigDecimal(d.getValue()));
                this.cashBoxDetail.setAmount(this.cashBoxDetail.getValuer().multiply(BigDecimal.valueOf(this.cashBoxDetail.getQuantity())));
                this.cashBoxPartial.addCashBoxDetail(this.cashBoxDetail);//Agregar el CashBoxDetail al CashBox Instanciado
            }
        }
        //Ordenar la lista de denominaciones según su valor
        Collections.sort(this.cashBoxPartial.getCashBoxDetails(), Collections.reverseOrder((CashBoxDetail cashBoxDetail1, CashBoxDetail other) -> cashBoxDetail1.getValuer().compareTo(other.getValuer())));
        chargeListCashBoxBillMoney();
        if (this.cashBoxPartial.getPriority_order() == null) {
            this.cashBoxPartial.setPriority_order(CashBoxPartial.Priority.MAIN);
        }
        if (this.cashBoxGeneral.getId() == null) {
            this.cashBoxPartial.setPriority(0);
        } else {
            this.cashBoxPartial.setPriority((int) cashBoxPartialService.count("CashBoxPartial.countCashBoxPartialByCashBoxGeneral", this.cashBoxGeneral));
        }
    }

    private void chargeListCashBoxBillMoney() {
        this.cashBoxBills = new ArrayList<>();
        this.cashBoxMoneys = new ArrayList<>();
        for (CashBoxDetail cashboxDetail : this.cashBoxPartial.getCashBoxDetails()) {
            if (cashboxDetail.getDenomination_type().equals(CashBoxDetail.DenominationType.BILL)) {
                this.cashBoxBills.add(cashboxDetail);
            } else if (cashboxDetail.getDenomination_type().equals(CashBoxDetail.DenominationType.MONEY)) {
                this.cashBoxMoneys.add(cashboxDetail);
            }
        }
    }

    public void openPanelBreakdown() { //Construir el Panel de Detalle
        setActiveButtonBreakdown(true); //Deshabilitar el botón de inicio de desglose
        setActivePanelBreakdown(true); //Mostrar el Panel de Detalle de CashBox Instanciado
//        this.activePanelBreakdown = true; //Mostrar el Panel de Detalle de CashBox Instanciado
//        this.activeSelectDeposit = false; //Desactivar el Detalle del Panel de Depósito
//        this.activePanelDeposit = false; //Ocultar el Panel de Depósito
//        this.activeButtonBreakdown = false;
        setActiveIndex(-1);
        isActiveButtonCloseCash(); //Deshabilitar el Botón de CashBox General
        this.addInfoMessage(I18nUtil.getMessages("action.sucessfully"), I18nUtil.getMessages("common.start") + " " + I18nUtil.getMessages("app.fede.accounting.ajust.breakdown"));
        this.cashBoxPartial = cashBoxPartialService.createInstance();
        generateCashBoxPartialDetails();
    }

    public void calculateAmount(RowEditEvent<CashBoxDetail> event) {
        event.getObject().setAmount(event.getObject().getValuer().multiply(BigDecimal.valueOf(event.getObject().getQuantity())));
        this.addSuccessMessage(I18nUtil.getMessages("action.sucessfully"), "La cantidad de Billetes/Monedas de" + event.getObject().getDenomination() + " es: " + event.getObject().getQuantity());
        if (this.cashBoxPartial.getPriority_order().equals(CashBoxPartial.Priority.MAIN)) {
            calculateTotals(this.saldoCash);
        } else if (this.cashBoxPartial.getPriority_order().equals(CashBoxPartial.Priority.SECONDARY)) {
            calculateTotals(this.saldoCashFund);
        }
    }

    private void calculateTotals(BigDecimal saldoCashPrincipal) {
        this.cashBoxPartial.setTotalcashBills(BigDecimal.ZERO);
        this.cashBoxPartial.setTotalcashMoneys(BigDecimal.ZERO);
        this.cashBoxPartial.setMissCashPartial(BigDecimal.ZERO);
        this.cashBoxPartial.setExcessCashPartial(BigDecimal.ZERO);
        for (CashBoxDetail cashBoxBill : this.cashBoxBills) {
            if (cashBoxBill.getQuantity() > 0) {
                for (CashBoxDetail cashBoxDetail : this.cashBoxPartial.getCashBoxDetails()) {
                    if (cashBoxBill.getDenomination().equals(cashBoxDetail.getDenomination())) {
                        cashBoxDetail.setQuantity(cashBoxBill.getQuantity());
                        cashBoxDetail.setAmount(cashBoxBill.getAmount());
                    }
                }
            }
//            if (this.cashBoxPartial.getCashBoxDetails().contains(cashBoxBill)) {
//                this.cashBoxPartial.addCashBoxDetail(cashBoxBill);
//                this.cashBoxPartial.setTotalcashBills(this.cashBoxPartial.getTotalcashBills().add(cashBoxBill.getAmount()));
//            }
        }
        for (CashBoxDetail cashBoxMoney : this.cashBoxMoneys) {
            if (cashBoxMoney.getQuantity() > 0) {
                for (CashBoxDetail cashBoxDetail : this.cashBoxPartial.getCashBoxDetails()) {
                    if (cashBoxMoney.getDenomination().equals(cashBoxDetail.getDenomination())) {
                        cashBoxDetail.setQuantity(cashBoxMoney.getQuantity());
                        cashBoxDetail.setAmount(cashBoxMoney.getAmount());
                    }
                }
            }
//            if (this.cashBoxPartial.getCashBoxDetails().contains(cashBoxBill)) {
//                this.cashBoxPartial.addCashBoxDetail(cashBoxBill);
//                this.cashBoxPartial.setTotalcashBills(this.cashBoxPartial.getTotalcashBills().add(cashBoxBill.getAmount()));
//            }
        }
//        for (CashBoxDetail cashBoxMoney : this.cashBoxMoneys) {
//            if (this.cashBoxPartial.getCashBoxDetails().contains(cashBoxMoney)) {
//                this.cashBoxPartial.addCashBoxDetail(cashBoxMoney);
//                this.cashBoxPartial.setTotalcashMoneys(this.cashBoxPartial.getTotalcashMoneys().add(cashBoxMoney.getAmount()));
//            }
//        }
        for (CashBoxDetail cashBoxDetail1 : this.cashBoxPartial.getCashBoxDetails()) {
            if (cashBoxDetail1.getDenomination_type().equals(CashBoxDetail.DenominationType.BILL)) {
                this.cashBoxPartial.setTotalcashBills(this.cashBoxPartial.getTotalcashBills().add(cashBoxDetail1.getAmount()));
            } else if (cashBoxDetail1.getDenomination_type().equals(CashBoxDetail.DenominationType.MONEY)) {
                this.cashBoxPartial.setTotalcashMoneys(this.cashBoxPartial.getTotalcashMoneys().add(cashBoxDetail1.getAmount()));
            }
        }
        this.cashBoxPartial.setTotalCashBreakdown(this.cashBoxPartial.getTotalcashBills().add(this.cashBoxPartial.getTotalcashMoneys()));

        switch (this.cashBoxPartial.getTotalCashBreakdown().compareTo(saldoCashPrincipal)) {
            case 0:
                this.cashBoxPartial.setMissCashPartial(BigDecimal.ZERO);
                this.cashBoxPartial.setExcessCashPartial(BigDecimal.ZERO);
                break;
            case -1:
                this.cashBoxPartial.setMissCashPartial(this.cashBoxPartial.getTotalCashBreakdown().subtract(saldoCashPrincipal));
                this.cashBoxPartial.setExcessCashPartial(BigDecimal.ZERO);
                break;
            case 1:
                this.cashBoxPartial.setMissCashPartial(BigDecimal.ZERO);
                this.cashBoxPartial.setExcessCashPartial(this.cashBoxPartial.getTotalCashBreakdown().subtract(saldoCashPrincipal));
                break;
            default:
                break;
        }
        this.cashBoxPartial.setMissCashPartial(this.cashBoxPartial.getMissCashPartial().multiply(BigDecimal.valueOf(-1)));//Guardar valores positivos
        this.cashBoxPartial.setSaldoPartial(saldoCashPrincipal);
    }

    private void updateProperties() { //Cargar los atributos del CashBoxParcial y CashBoxGeneral Instanciado
        this.cashBoxPartial.setCashPartial(this.cashTotal);
        this.cashBoxPartial.setLastUpdate(Dates.now());
        if (this.cashBoxPartial.getId() == null) {
            this.cashBoxPartial.setAuthor(this.subject);
            this.cashBoxPartial.setOwner(this.subject);
            this.cashBoxPartial.setName(I18nUtil.getMessages("app.fede.accounting.close.cash") + " " + I18nUtil.getMessages("common.of") + " " + this.subject.getFirstname() + " " + this.subject.getSurname());
            if (this.cashBoxPartial.getStatusCashBoxPartial() == null) {
                this.cashBoxPartial.setStatusCashBoxPartial(CashBoxPartial.Status.OPEN);
            }
        }
        this.cashBoxGeneral.setCashFinal(this.cashBoxPartial.getCashPartial());
        this.cashBoxGeneral.setSaldoFinal(this.cashBoxPartial.getSaldoPartial());
        this.cashBoxGeneral.setTotalBreakdownFinal(this.cashBoxPartial.getTotalCashBreakdown());
        this.cashBoxGeneral.setMissCashFinal(this.cashBoxPartial.getMissCashPartial());
        this.cashBoxGeneral.setExcessCashFinal(this.cashBoxPartial.getExcessCashPartial());
        this.cashBoxGeneral.setName(I18nUtil.getMessages("app.fede.accounting.close.cash") + " " + this.organizationData.getOrganization().getInitials() + "/" + Dates.toDateString(Dates.now()));
    }

    public void save() { //Guardar el CashBoxGeneral, con todos sus CashBoxs y CashBoxDetails
        updateProperties(); //Cargar los atributos del CashBoxParcial y CashBoxGeneral Instanciado
        this.cashBoxGeneral.addCashBoxPartial(this.cashBoxPartial); //Agregar un CashBox al CashBoxGeneral
        if (this.cashBoxGeneral.isPersistent()) {
            this.cashBoxGeneral.setLastUpdate(Dates.now());
            this.cashBoxGeneral.setAuthor(this.subject); //Actualiza, para saber que sujeto lo cierra por última vez
        } else {
            this.cashBoxGeneral.setAuthor(this.subject);
            this.cashBoxGeneral.setOwner(this.subject);
            this.cashBoxGeneral.setStatusCashBoxGeneral(CashBoxGeneral.Status.OPEN);
            this.cashBoxGeneral.setAccount(this.selectedAccount);
            this.cashBoxGeneral.setOrganization(this.organizationData.getOrganization());
        }
        cashBoxGeneralService.save(this.cashBoxGeneral.getId(), this.cashBoxGeneral);
        this.addSuccessMessage(I18nUtil.getMessages("action.sucessfully"), "Desglose de Efectivo guardado correctamente!");
        this.cashBoxGeneral = cashBoxGeneralService.find(this.cashBoxGeneral.getId());//Recargar los objetos
        if (this.cashBoxPartial.getId() != null) {
            this.cashBoxPartial = cashBoxPartialService.find(this.cashBoxPartial.getId());
        }
    }

    public void closeCashBoxChecker() {
        this.cashBoxPartial.setStatusCashBoxPartial(CashBoxPartial.Status.CLOSED); //Cambiar el estado del CashBox Instanciado (Cerrar)
        save(); //Guardar el CashBoxGeneral, con todos sus CashBoxs y CashBoxDetails
//        this.activePanelBreakdown = false;
        this.saldoCashFund = this.cashBoxPartial.getTotalCashBreakdown(); //Saldo en efectivo más o menos el exceso y faltante de dinero
//        this.activePanelDeposit = true; //Preparar para iniciar el déposito y el nuevo Desgloce de depósito
        if (this.cashBoxPartial.getPriority_order().equals(CashBoxPartial.Priority.SECONDARY)) {
//            this.activePanelBreakdownFund = false;
        }
        setActivePanelBreakdownFund(false);//ocultar el Panel de desglose Fund;
        updateFinishCashBoxPartial();
        findCashBoxs();
        getCashBoxInitialFinish(); //Recargar el último cashBoxPartial
        getCashBoxsClosed(); //Recargar lista de CashBox Cerrados
        isActiveButtonBreakdown();
        isActiveButtonCloseCash();
        isPanelVerification();
//        this.cashBoxPartial = cashBoxPartialService.createInstance();
    }

    public void updateFinishCashBoxPartial() {
        if (this.cashBoxGeneral.getId() != null) { //Buscar el último cashBoxPartial para marcarlo como final
            int partialsFinal = (int) cashBoxPartialService.count("CashBoxPartial.countCashBoxPartialByCashBoxGeneral", this.cashBoxGeneral);
            for (int i = 0; i < this.cashBoxGeneral.getCashBoxPartials().size(); i++) {
                if (this.cashBoxGeneral.getCashBoxPartials().get(i).getPriority().compareTo(partialsFinal - 1) == 0) {
                    this.cashBoxGeneral.getCashBoxPartials().get(i).setStatus_priority(CashBoxPartial.StatusPriority.FINAL);
                } else {
                    this.cashBoxGeneral.getCashBoxPartials().get(i).setStatus_priority(CashBoxPartial.StatusPriority.INTERMEDIATE);
                }
            }
        }
        cashBoxGeneralService.save(this.cashBoxGeneral.getId(), this.cashBoxGeneral); //Guardar el CashBoxGeneral, con el cambio en el cashBoxPartial
        this.cashBoxGeneral = cashBoxGeneralService.find(this.cashBoxGeneral.getId());//Recargar los objetos
        if (this.cashBoxPartial.getId() != null) {
            this.cashBoxPartial = cashBoxPartialService.find(this.cashBoxPartial.getId());
        }
    }

    public void closeCashBoxGeneral() {
        this.cashBoxGeneral.setStatusCashBoxGeneral(CashBoxGeneral.Status.CLOSED); //Cambiar el estado del CashBoxGeneral Instanciado (Cerrar)
        cashBoxGeneralService.save(this.cashBoxGeneral.getId(), this.cashBoxGeneral); //Guardar el CashBoxGeneral, con todos sus CashBoxs y CashBoxDetails
//        this.activeButtonBreakdown = false;
//        this.activePanelDeposit = false;//Ocultar el Panel de Depósito
        isPanelVerification();
    }

    //REGISTRO DE ASIENTOS CONTABLES
    public void cleanPanelDeposit() { //Resetear los valores del Panel de Depósito
        this.depositAccount = null;
        this.amountDeposit = BigDecimal.ZERO;
//        this.activeButtonSelectDeposit = true; //Deshaibiltar el Button y Select del Panel de Depósito
    }

    public void validateAmountDeposit() { //Validar monto de depósito (Mensajes de validación)
        if (this.amountDeposit != null) {
            this.activeButtonSelectDeposit = !(this.amountDeposit.compareTo(BigDecimal.ZERO) == 1 && (this.amountDeposit.compareTo(this.saldoCashFund) == 0 || this.amountDeposit.compareTo(this.saldoCashFund) == -1)); //Activar/Desactivar Select y Botón de Depósito
            if (this.amountDeposit.compareTo(BigDecimal.ZERO) == 1) {
                if (this.amountDeposit.compareTo(this.saldoCashFund) == 1) {
                    this.addWarningMessage(I18nUtil.getMessages("action.warning"), I18nUtil.getMessages("app.fede.accouting.validate.deposit.amount.greater") + "Efectivo Registrado" + ": $" + this.saldoCashFund);
                }
            } else {
                this.addWarningMessage(I18nUtil.getMessages("action.warning"), I18nUtil.getMessages("app.fede.accouting.validate.deposit.amount.less.zero"));
            }
        }
    }

    public void validateDeposit() {
//        this.activePanelBreakdownFund = false;
        if (this.depositAccount != null) {
            if (this.depositAccount.getId().equals(this.selectedAccount.getId())) {
                this.addErrorMessage(I18nUtil.getMessages("action.fail"), I18nUtil.getMessages("app.fede.accouting.validate.deposit.account.equals"));
//                this.activePanelBreakdownFund = false;
            } else {
                registerRecordInJournal(this.selectedAccount, this.depositAccount, this.amountDeposit); //Registrar asiento contable del depósito del valor de caja
                calculeSummaryToday();//Calcular el resumen de dinero con las transacciones
                this.cashBoxPartial = cashBoxPartialService.createInstance();
                setActiveIndex(-1);
                generateCashBoxPartialFund();
                cleanPanelDeposit();
                setActiveSelectDeposit(false);//Ocultar el Panel de depósito
                setActiveButtonBreakdown(true);//Deshabilitar el botón de desglose
                setActivePanelBreakdownFund(true); //Mostrar el Panel de desglose Fund
//                this.activePanelBreakdownFund = true;
//                this.activeSelectDeposit = false;
            }
        } else {
            this.addErrorMessage(I18nUtil.getMessages("action.fail"), I18nUtil.getMessages("app.fede.accouting.validate.deposit.account"));
//            this.activePanelBreakdownFund = false;
        }
    }

    private void generateCashBoxPartialFund() {
        this.saldoCashFund = this.saldoCash.add(this.cashBoxGeneral.getExcessCashFinal().subtract(this.cashBoxGeneral.getMissCashFinal())); //Saldo en efectivo más o menos el exceso y faltante de dinero
        generateCashBoxPartialDetails();
        this.cashBoxPartial.setPriority_order(CashBoxPartial.Priority.SECONDARY);
        this.cashBoxPartial.setDescription("$ " + this.saldoCashFund + " restantes, tras el depósito de $" + this.amountDeposit + " en " + this.depositAccount.getName());
    }

    public void verificatedCorrectFund() {
        if (getCashBoxInitialFinish() != null) {
            this.cashBoxInitialFinish.setVerification_TotalBreakdown(CashBoxPartial.Verification.CORRECT);
            this.cashBoxInitialFinish.getCashBoxGeneral().addCashBoxPartial(this.cashBoxInitialFinish);
            cashBoxGeneralService.save(this.cashBoxInitialFinish.getCashBoxGeneral().getId(), this.cashBoxInitialFinish.getCashBoxGeneral());
            this.addSuccessMessage(I18nUtil.getMessages("action.sucessfully"), "Gracias por su verificación, ahora puede iniciar el desglose actual");
//            activeButtonBreakdown = true; //Habilitar el botón de desglose
            generatePartialFinalToInitial();//Crear el nuevo cashBoxPartial con los datos del último CashBoxPartial Final
            isPanelVerification();
        }
    }

    public void verificatedIncorrectFund() {
        if (getCashBoxInitialFinish() != null) {
            this.cashBoxInitialFinish.setVerification_TotalBreakdown(CashBoxPartial.Verification.INCORRECT);
            this.cashBoxInitialFinish.getCashBoxGeneral().addCashBoxPartial(this.cashBoxInitialFinish);
            cashBoxGeneralService.save(this.cashBoxInitialFinish.getCashBoxGeneral().getId(), this.cashBoxInitialFinish.getCashBoxGeneral());
            this.addSuccessMessage(I18nUtil.getMessages("action.sucessfully"), "Gracias por su verificación, ahora puede iniciar el desglose actual");
//            activeButtonBreakdown = true; //Habilitar el botón de desglose
            generatePartialFinalToInitial();//Crear el nuevo cashBoxPartial con los datos del último CashBoxPartial Final
            isPanelVerification();
        }
    }

    private void generatePartialFinalToInitial() {
        generateCashBoxPartialDetails();
        for (CashBoxDetail cashBoxDetailFinal : this.cashBoxInitialFinish.getCashBoxDetails()) {
            if (cashBoxDetailFinal.getQuantity() > 0) {
                for (CashBoxDetail cashBoxDetail : this.cashBoxPartial.getCashBoxDetails()) {
                    if (cashBoxDetailFinal.getDenomination().equals(cashBoxDetail.getDenomination())) {
                        cashBoxDetail.setQuantity(cashBoxDetailFinal.getQuantity());
                        cashBoxDetail.setAmount(cashBoxDetailFinal.getAmount());
                    }
                }
            }
        }
        chargeListCashBoxBillMoney();
        calculateTotals(saldoCash);
        setActiveIndex(-1);
//        activePanelBreakdown = true;
//        this.activePanelDeposit = false;
    }

    public void messageValidate() {
        if (isPanelVerification() == true) {
            this.addWarningMessage(I18nUtil.getMessages("action.warning"), "Por favor primero verifique el Saldo del último Registro de Efectivo Finalizado, antes de empezar el nuevo desglose de caja");
//            activeButtonBreakdown = false; //Deshabilitar el botón de desglose
        }
    }

    public void registerRecordInJournal(Account selectedAccount, Account depositAccount, BigDecimal amountDeposit) { //Registar asiento contable
        GeneralJournal journal = buildFindJournal(); //Crear/Buscar el journal y el record, donde insertar los recordDetails
        Record record = buildRecord();
        record.addRecordDetail(updateRecordDetail(selectedAccount, amountDeposit, RecordDetail.RecordTDetailType.HABER));//Crear/Modificar un RecordDetail al Record del Journal del Día
        record.addRecordDetail(updateRecordDetail(depositAccount, amountDeposit, RecordDetail.RecordTDetailType.DEBE));
        record.setDescription((I18nUtil.getMessages("app.fede.accounting.transfer.from") + selectedAccount.getName() + " " + I18nUtil.getMessages("common.to.a") + " " + depositAccount.getName()).toUpperCase());
        journal.addRecord(record);

        GeneralJournal save = journalService.save(journal.getId(), journal); //Validar la inserción del Record
        if (save != null) {
            this.addInfoMessage(I18nUtil.getMessages("action.sucessfully"), I18nUtil.getMessages("app.fede.accounting.record.sucessfully") + " a las: " + Dates.toTimeString(Dates.now()));
        } else {
            this.addInfoMessage(I18nUtil.getMessages("action.fail"), I18nUtil.getMessages("app.fede.accounting.record.fail"));
        }
    }

    private RecordDetail updateRecordDetail(Account account, BigDecimal amountDeposit, RecordDetail.RecordTDetailType type) {
        RecordDetail recordDetailGeneral = recordDetailService.createInstance();
        if (amountDeposit.compareTo(BigDecimal.ZERO) == 1) {
            recordDetailGeneral.setOwner(this.subject);
            recordDetailGeneral.setAmount(amountDeposit.abs());
            recordDetailGeneral.setAccount(account);
            if (type.equals(RecordDetail.RecordTDetailType.DEBE)) {
                recordDetailGeneral.setRecordDetailType(RecordDetail.RecordTDetailType.DEBE);
            } else if (type.equals(RecordDetail.RecordTDetailType.HABER)) {
                recordDetailGeneral.setRecordDetailType(RecordDetail.RecordTDetailType.HABER);
            }
        }
        return recordDetailGeneral;
    }

    private GeneralJournal buildFindJournal() {
        GeneralJournal generalJournal = journalService.findUniqueByNamedQuery("Journal.findByCreatedOnAndOrg", Dates.minimumDate(Dates.now()), Dates.now(), this.organizationData.getOrganization());
        if (generalJournal == null) {
            generalJournal = journalService.createInstance();
            generalJournal.setOrganization(this.organizationData.getOrganization());
            generalJournal.setOwner(this.subject);
            generalJournal.setCode(UUID.randomUUID().toString());
            generalJournal.setName(I18nUtil.getMessages("app.fede.accounting.journal") + " " + this.organizationData.getOrganization().getInitials() + "/" + Dates.toDateString(Dates.now()));
            journalService.save(generalJournal); //Guardar el journal creado
            generalJournal = journalService.findUniqueByNamedQuery("Journal.findByCreatedOnAndOrg", Dates.minimumDate(Dates.now()), Dates.now(), this.organizationData.getOrganization());
        }
        return generalJournal;
    }

    private Record buildRecord() {
        Record recordGeneral = recordService.createInstance();
        recordGeneral.setOwner(this.subject);
        return recordGeneral;
    }

    @Override
    public void handleReturn(SelectEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Group getDefaultGroup() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void initializeDateInterval() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Group> getGroups() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
