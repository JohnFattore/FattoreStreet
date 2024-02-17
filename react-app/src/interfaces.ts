export interface IAllocation {
    ticker: string; shares: number;
}

export interface IAsset {
    ticker: string,
    shares: number,
    costbasis: number,
    buy: string
}

export interface IOption {
    ticker: string,
    sunday: string
}

export interface IQuote {
    price: number,
    percentChange: number
}