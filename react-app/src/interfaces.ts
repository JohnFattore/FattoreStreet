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

export interface ISECData {
    ticker: string;
    cik: string;
    ttmNetIncomeYoY: string;
    latestQuarterEnd: string;
    ttmRevenueYoY: string;
    ttmNetIncome: string;
    ttmRevenue: string;
}