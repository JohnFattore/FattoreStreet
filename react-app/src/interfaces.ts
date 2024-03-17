export interface IAllocation {
    ticker: string; 
    shares: number;
}

export interface IAsset {
    ticker: string,
    shares: number,
    costbasis: number,
    buy: string,
    id: number,
}

export interface IQuote {
    price: number,
    percentChange: number
}

export interface IMessage {
    text: string,
    type: string
}

export interface IOption {
    ticker: string,
    sunday: string,
    id: number
}

export interface ISelection {
    option: number,
    user: number,
    id: number
}