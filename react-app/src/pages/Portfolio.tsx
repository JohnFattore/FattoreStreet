import PortfolioHeader from '../components/PortfolioHeader'
import AssetTable from '../components/AssetTable';
import AssetForm from '../components/AssetForm';
import AssetFormHeader from '../components/AssetFormHeader';
import React from 'react';


function Portfolio() {
    const [change, setChange] = React.useState(false)
    return (
        <>
            <PortfolioHeader />
            <AssetTable change={change} setChange={setChange}/>
            <AssetFormHeader />
            <AssetForm setChange={setChange}/>
        </>

    );
  }
  
  export default Portfolio;