import CreateAccountForm from "../components/CreateAccountForm";
import AccountList from "../components/AccountList";
import YahooFinanceBanner from "../components/YahooFinanceBanner";
import FinnhubBanner from "../components/FinnhubBanner";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginRequired from "../components/LoginRequired";

export default function Portfolio() {
  const { access } = useSelector((state: RootState) => state.user);

  if (!access) {
    return (
      <LoginRequired
        title="Protected Area"
        message="You must be logged in to view and manage your portfolio."
        buttonText="Sign In to Continue"
      />
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
