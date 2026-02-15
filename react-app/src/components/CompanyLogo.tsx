import { Image } from "react-bootstrap";

export default function CompanyLogo({ ticker }: { ticker: string }) {
  return (
    <Image
      src={`https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/${ticker}.png`}
      alt={`${ticker} logo`}
    />
  );
}
