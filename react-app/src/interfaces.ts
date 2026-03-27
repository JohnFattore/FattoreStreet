export interface IChatMessage {
    role: 'user' | 'model';
    text: string;
    timestamp?: string;
}

export interface IAllocation {
    ticker: string;
    shares: number;
}

export interface IAsset {
    id: number,
    ticker: string,
    shares: number,
    buyDate: string,
    buyPrice: number,
    snp500PriceBuy: number,
    sellDate: string | null,
    sellPrice: number | null,
    snp500PriceSell: number | null,
    account: number | null,
}

export interface ISnP500Price {
    date: string,
    costBasis: number,
    currentPrice: number,
    percentChange: number,
    id: number,
}

export interface IQuote {
    price: number,
    percentChange: number
}

export interface IEquityInfo {
    ticker: string;
    shortName: string;
    longName: string;
    type: string;
    exchange: string;
    market: string;
    currentPrice: number,
    percentChangeDaily: number,
    percentChangeWeekly: number,
    percentChangeMonthly: number,
    percentChangeYTD: number,
    percentChangeYearly: number,
    percentChange3Years: number,
    percentChange5Years: number,
    dividendYield: number,
    marketCap: number;
    trailingPE: number;
    incomeTTM: number;
    revenueTTM: number;
    netMarginTTM: number;
}

export interface IETFInfo {
    ticker: string;
    shortName: string;
    longName: string;
    type: string;
    exchange: string;
    market: string;
    currentPrice: number,
    percentChangeDaily: number,
    percentChangeWeekly: number,
    percentChangeMonthly: number,
    percentChangeYTD: number,
    percentChangeYearly: number,
    percentChange3Years: number,
    percentChange5Years: number,
    marketCap: number;
    trailingPE: number;
    expenseRatio: number;
    dividendYield: number,
}

export interface IAlbum {
    name: string;
    artist: string;
    year: number;
}


export interface IBrokerage {
    name: string;
    mmf: string;
    us: string;
    inter: string;
    id: number
}

export interface IRatio {
    name: string;
    formula: string;
    description: string;
}

/******************************************* Restaurant *************************************************/

export interface IRestaurant {
    yelp_id: string;
    name: string;
    address: string;
    state: string;
    city: string;
    latitude: number;
    longitude: number;
    categories: string;
    stars: string;
    review_count: number;
    id: number;
}

export interface IReview {
    restaurant: number;
    name: string;
    user: number;
    rating: number;
    comment: string;
    latitude: number;
    longitude: number;
    id: number;
}

export interface IMenuItem {
    restaurant: string;
    name: string;
    description: string;
    price: number;
    id: number;
}

export interface IFavorite {
    menu_item: string;
    user: number;
    comment: string;
    id: number;
}

export interface IFilingSummary {
    filingDate: string;
    accessionNumber: string;
    summary: string;
}

export interface IIexPrice {
    date: string;
    open: number;
    high: number;
    low: number;
    close: number;
    adjustedOpen?: number;
    adjustedHigh?: number;
    adjustedLow?: number;
    adjustedClose?: number;
    volume: number;
}

export interface IIexPricesResponse {
    ticker: string;
    prices: IIexPrice[];
}

export interface IDividendRow {
    date: string;
    value: number;
    rawValue?: number;
    adjustedValue?: number;
    source?: string;
    formType?: string;
    accessionNumber?: string;
    recordDate?: string;
    payDate?: string;
    confidenceScore?: number;
}

export interface IIexDividendsResponse {
    ticker: string;
    dividends: IDividendRow[];
}

export interface ISplitRow {
    date: string;
    value?: number;
    ratio?: number;
    source?: string;
    formType?: string;
    accessionNumber?: string;
    confidenceScore?: number;
}

export interface IIexSplitsResponse {
    ticker: string;
    splits: ISplitRow[];
}

export interface IMarketIndex {
    code: string;
    displayName: string;
}

export interface IIndexMemberStock {
    ticker: string;
    name: string;
    marketCap: number;
    volume: number;
    volumeUSD: number;
    freeFloat: number;
    freeFloatMarketCap: number;
    countryIncorp: string;
    countryHQ: string;
    securityType: string;
    yearIPO: number;
}

export interface IIndexMemberRow {
    id: number;
    percent: number;
    index: string;
    outlier: boolean;
    notes: string;
    stock: IIndexMemberStock;
}

export interface ISECData {
    ticker: string;
    cik: string;
    ttmNetIncome: string;
    ttmRevenue: string;
    ttmOperatingCashFlow: string;
    ttmOperatingIncome: string;
    ttmGrossProfit: string;
    ttmNetIncomeYoY: string;
    ttmRevenueYoY: string;
    latestAssets: string;
    latestLiabilities: string;
    latestEquity: string;
    latestInventory: string;
    latestCash: string;
    latestEps: string;
    netMargin: string;
    grossMargin: string;
    debtToAssets: string;
    cashToLiabilities: string;
    roA: string;
    ocfToNetIncome: string;
    latestQuarterEnd: string;
}

export interface IQuarter {
    year: number;
    quarter: string;
    periodStart: string;
    periodEnd: string;
    revenues: number;
    netIncomeLoss: number;
    operatingIncomeLoss: number;
    grossProfit: number;
    epsBasic: number;
    epsDiluted: number;
    assets: number;
    liabilities: number;
    equity: number;
    cash: number;
    receivables: number;
    inventory: number;
    ocf: number;
    dividends: number;
    buybacks: number;
}

export interface ISECQuartersResponse {
    ticker: string;
    cik: string;
    quarters: IQuarter[];
}

export interface IYFinanceQuarter {
    year: number;
    quarter: number;
    periodEnd: string;
    revenues: number | null;
    netIncomeLoss: number | null;
    operatingIncomeLoss: number | null;
    grossProfit: number | null;
    earningsPerShareBasic: number | null;
    earningsPerShareDiluted: number | null;
    assets: number | null;
    liabilities: number | null;
    stockholdersEquity: number | null;
    cashAndCashEquivalentsAtCarryingValue: number | null;
    accountsReceivableNetCurrent: number | null;
    inventoryNet: number | null;
    netCashProvidedByUsedInOperatingActivities: number | null;
    paymentsOfDividends: number | null;
    paymentsForRepurchaseOfCommonStock: number | null;
}