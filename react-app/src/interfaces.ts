export interface IAllocation {
    ticker: string; 
    shares: number;
}

export interface IAsset {
    ticker: string,
    short_name: string,
    long_name: string,
    type: string,
    exchange: string,
    market: string,
    shares: number,
    costBasis: number,
    sellPrice: number | null,
    buyDate: string,
    sellDate: string | null,
    currentPrice: number,
    percentChange: number,
    snp500PriceBuy: number,
    snp500PriceSell: number | null,
    snp500PercentChange: number,
    id: number,
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

export interface IAlbum {
    name: string;
    artist: string;
    year: number;
}

// outdated
export interface IOutlier {
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
    notes: string;
    id: number
}

export interface IIndexMember {
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
    percent: number;
    index: string;
    outlier: boolean;
    notes: string;
    id: number
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