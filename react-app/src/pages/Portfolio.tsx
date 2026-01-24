import CreateAccountForm from "../components/CreateAccountForm";
import AccountList from "../components/AccountList";
import YahooFinanceBanner from "../components/YahooFinanceBanner";
import FinnhubBanner from "../components/FinnhubBanner";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "../components/LoginForm";
import { Alert } from "react-bootstrap";

export default function Portfolio() {
  const { access } = useSelector((state: RootState) => state.user);

  if (!access) {
    return (
      <>
        <Alert variant="info">Please login to view your portfolio.</Alert>
        <LoginForm />
      </>
    );
  }

  return (
    <>
      <AccountList />
      <CreateAccountForm />
      <YahooFinanceBanner />
      <FinnhubBanner />
    </>
  );
}
