/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

package org.drugepi.hdps;

import java.util.ArrayList;
import java.util.List;

import org.drugepi.PharmacoepiTool;
import org.drugepi.hdps.db.HdpsDbController;
import org.drugepi.hdps.local.HdpsLocalController;
import org.drugepi.util.RowReader;


/**
 * Implementation of the hd-PS algorithm.
 * <p>
 * The algorithm is fully described in:
 * Schneeweiss S, Rassen JA, Glynn RJ, Avorn J, Mogun H, Brookhart MA. High-dimensional propensity score 
 * adjustment in studies of treatment effects using health care claims data. <I>Epidemiology</I> 2009;20:512-22.
 * <p>
 * Version 2 of the algorithm is described in Rassen JA, Schneewiss S, et al.:
 * Rassen JA, Glynn RJ, Brookhart MA, Schneeweiss S. Observed performance of high-dimensional propensity 
 * score analyses of treatment effects in small samples.  In submission 2010.
 * <p>
 * This class can be used alone or in conjunction with SAS or R.  For full documentation, please see
 * the drugepi.org or hdhub.org web sites.
 * <p>
 * Parameters to the algorithm can be specified by setting public variables, or by setter functions.
 * Setter functions that take doubles rather than ints are provided for rJava compatibility.
 * <p>
 * Patient information is specified through the {@link org.drugepi.PharmacoepiTool} addPatient method.
 * Patient data must be specified in the following order:
 * <ul>
 * 	<li>patient ID
 * 	<li>exposure status (1=exposed, 0=unexposed)
 * 	<li>outcome status (1=outcome; 0=no outcome)
 * </ul>
 * <p>
 * Dimension information is specified through the {@link #addDimension(String, RowReader)} or 
 * {@link #addDimension(String, String, String, String, String, String)} methods.
 * Dimension data must be specified in the following order:
 * <ul>
 * 	<li>patient ID
 * 	<li>code (e.g., diagnosis code, drug name, etc.)
 * 	<li>date that code occurred (value currently ignored; can leave as 0)
 * </ul>
 * 
 * @author Jeremy A. Rassen
 * @version 2.1.0
 * 
 */
public class Hdps extends PharmacoepiTool 
{
	/**
	 * Description string.
	 */
	public final String description = "High-Dimensional Propensity Score Variable Selection Module";

	/**
	 * Constant string indicating local mode operation. 
	 */
	public static final String hdpsModeLocal = "LOCAL";

	/**
	 * Constant string indicating database mode operation. 
	 */
	public static final String hdpsModeDB = "DB";
	
	/**
	 * Constant string indicating bias-based variable ranking. 
	 */
	public static final String RANKING_METHOD_BIAS = "BIAS";

	/**
	 * Constant string indicating exposure-association-based variable ranking. 
	 */
	public static final String RANKING_METHOD_EXP = "EXP_ASSOC";
	
	/**
	 * Constant string indicating outcome-association-based variable ranking. 
	 */
	public static final String RANKING_METHOD_OUTCOME = "OUTCOME_ASSOC";
	
	/**
	 * Constant string indicating no particular variable ranking; this
	 * is meant to be used when particular variables are selected. 
	 */
	public static final String RANKING_METHOD_NONE = "NO_RANKING";
	
	/**
	 * Constant string indicating dichotomous/binary outcome type. 
	 */
	public static final String OUTCOME_TYPE_DICHOTOMOUS = "DICHOTOMOUS";

	/**
	 * Constant string indicating dichotomous/binary outcome type. 
	 */
	public static final String OUTCOME_TYPE_BINARY = "BINARY";

	/**
	 * Constant string indicating count outcome type. 
	 */
	public static final String OUTCOME_TYPE_COUNT = "COUNT";
	
	/**
	 * Constant string indicating continuous outcome type. 
	 */
	public static final String OUTCOME_TYPE_CONTINUOUS = "CONTINUOUS";


	/**
	 * The <i>n</i> most prevalent empirical covariates to consider from each dimension of data.  Default is 200.
	 */
	public int topN;

	/**
	 * The number of empirical covariates to include in the resulting propensity score.  Default is 500.
	 */
	public int k;
	

	/**
	 * The minimum number of times a variable has to appear in order to be considered for inclusion.
	 * Note that in all previous versions, this value was 100.  It now defaults to 0.
	 */
	public int frequencyMin;

	/**
	 * The type of variable ranking to do: BIAS, EXPOSURE_ASSOC, OUTCOME_ASSOC.  Default is BIAS.
	 */
	public String variableRankingMethod;

	/**
	 * An indicator for whether to screen variables based on their confounder/exposure association only.  Default is 0.
	 * NOTE: Deprecated.  Should now be specified as EXPOSURE_ASSOC ranking method.
	 */
	public int exposureOnlyScreen;
	
	/**
	 * An indicator for whether to screen variables with a zero correction added to each cell 
	 * in the confounder/outcome 2x2 table.  Recommended when the number of exposed outcomes 
	 * is less than 150.  Default is 0.
	 */
	public int useOutcomeZeroCellCorrection;

	/**
	 * An indicator for whether to include variables that describe the intensity of service 
	 * usage in each dimension. 
	 */
	public int inferServiceIntensityVars;
	
	/**
	 * An indicator for whether to create time interactions to "discount" variables that
	 * occur farther in the past.  EXPERIMENTAL ONLY. 
	 */
	public int createTimeInteractions;
	
	/**
	 * An indicator for whether to create profile scores to determine whether a variable  
	 * is gaining or losing frequency.  EXPERIMENTAL ONLY. 
	 */
	public int createProfileScores;
	
	/**
	 * The type of the outcome variable: DICHTOTOMOUS (or BINARY), or CONTINUOUS.  Default is DICHOTOMOUS.
	 */
	public String outcomeType;
	
	
	/**
	 * An indicator for whether the algorithm should output a full cohort with all variable information
	 * for all patients.  Default is 1.
	 */
	public int doFullOutput;
	
	/**
	 *  The name of the full output file, if full output has been requested.  Default is hdps_cohort.txt.
	 */
	public String fullOutputFilename = "output_full_cohort.txt";
	
	
	/**
	 * An indicator for whether the algorithm should output a sparse cohort with a variable list
	 * for each patient.  Default is 0.
	 */
	public int doSparseOutput;
	
	/**
	 *  The name of the sparse output file, if sparse output has been requested.  Default is hdps_sparse.txt.
	 */
	public String sparseOutputFilename = "output_sparse_cohort.txt";

	
	/**
	 * The path to a directory where the hd-PS algorithm can store temporary files.  There should be 
	 * enough space in the directory to hold a second copy of the input cohort and each of the dimensions.
	 * No default; must be specified.
	 */
	public String tempDirectory;
	
	
	/**
	 * Via a method below, there is also public access to the list of varibles to output.
	 * Users may specify hash codes of variables to include in any output cohort; these 
	 * variables will be in addition to any others normally output.
	 * 
	 */

	
	/*
	 * ===========================================
	 * PUBLIC VARIABLES FOR DB MODE 
	 * ===========================================
	 */
	public String dbDriverClass;
	public String dbUrl;
	public String dbUsername;
	public String dbPassword;
	
	/**
	 * Whether to maintain the output tables after the run.
	 */
	public int dbKeepOutputTables;
	
	
	/*
	 * ===========================================
	 * PROTECTED VARIABLES
	 * ===========================================
	 */
	protected static final int MAX_DIMENSIONS = 100;
	protected int numDimensions = 0;
	protected String mode;
	protected List<String> requestedVariables;

	private HdpsController hdpsController;
	
	/**
	 * Constructor for the hd-PS class using default values for all parameters.
	 */
	public Hdps()
	{
		super();
		this.k = 500;
		this.topN = 200;
		this.frequencyMin = 0;
		this.exposureOnlyScreen = 0;
		this.variableRankingMethod = RANKING_METHOD_BIAS;
		this.outcomeType = OUTCOME_TYPE_DICHOTOMOUS;
		this.useOutcomeZeroCellCorrection = 0;
		this.inferServiceIntensityVars = 0;
		this.doFullOutput = 1;
		this.doSparseOutput = 0;
		this.dbKeepOutputTables = 0;
		this.requestedVariables = new ArrayList<String>();
		try {
			this.setMode(Hdps.hdpsModeLocal);
		} catch (Exception e) {
			// no exceptions possible 
		}
	}
	
	/**
	 * Constructor for the hd-PS class using default values for all parameters.
	 * 
	 * @param tempDirectory		tempDirectory parameter.
	 */
	public Hdps(String tempDirectory)
	{
		this();
		this.tempDirectory = tempDirectory;
	}
	
	public void addPatients(RowReader reader)
	throws Exception 
	{
		this.hdpsController.addPatients(reader);
	}
		
	/**
	 * Add a dimension to the hd-PS run, with dimension data stored in a tab-delimited file.
	 * 
	 * @param description	Description of the dimension.
	 * @param filePath		Path of the dimension data file.  The file should contain three columns:
	 * 						patient_id, code, date.  Columns must be stored in this order.
	 * @throws Exception	
	 */
	public void addDimension(String description, String filePath)
	throws Exception
	{
		numDimensions++;
		
		if (numDimensions > Hdps.MAX_DIMENSIONS) 
			throw new HdpsException("Too many dimensions specified.");
		
		this.hdpsController.addDimension(description, filePath);
	}
	
	/**
	 * Add a dimension to the hd-PS run, with dimension data stored in a database.
	 * 
	 * @param description	Description of the dimension.
	 * @param dbDriverClass	Name of the database driver, or null to use the globally-specified default.
	 * @param dbURL			JDBC URL of the database, or null to use the globally-specified default
	 * @param dbUser		Database user name, or null to use the globally-specified default.
	 * @param dbPassword	Database user's password, or null to use the globally-specified default.
	 * @param dbQuery		The query that will result in the dimension data.  The query should return 
	 * 						three columns: patient_id, code, date.  Columns must be returned in this order.
	 * @throws Exception
	 */
	public void addDimension(String description, String dbDriverClass, 
							String dbURL, String dbUser, String dbPassword,
							String dbQuery)
	throws Exception
	{
		numDimensions++;
		
		if (numDimensions > Hdps.MAX_DIMENSIONS) 
			throw new HdpsException("Too many dimensions specified.");
		
		this.hdpsController.addDimension(description, 
				(dbDriverClass == null ? this.dbDriverClass : dbDriverClass), 
				(dbURL == null ? this.dbUrl : dbURL), 
				(dbUser == null ? this.dbUsername : dbUser), 
				(dbPassword == null ? this.dbPassword : dbPassword), 
				dbQuery);
	}
	
	
	/**
	 * Users may specify hash codes of variables to include in any variables to output.
	 * These variables will be in addition to any variables normally include in the output
	 * cohorts.
	 * 
	 * @param hashValue
	 */
	public void addRequestedVariable(String hashValue)
	{
		if (hashValue != null)
			this.requestedVariables.add(hashValue);
	}
	
	/*
	 * ===========================================
	 * PROTECTED METHODS
	 * ===========================================
	 */
	
	/**
	 * Begin execution of the hd-PS algorithm.
	 * 
	 * @throws Exception
	 */
	public void run()
    throws Exception
    {
		this.hdpsController.run();
    }
	
	/*
	 * ===========================================
	 * GETTERS AND SETTERS
	 * ===========================================
	 */
	
    /**
     * @see #topN 
     */
    public int getTopN() {
		return topN;
	}

	/**
     * @see #topN 
	 */
	public void setTopN(int topN) {
		this.topN = topN;
	}

	/**
     * @see #k 
	 */
	public int getK() {
		return k;
	}

	/**
     * @see #k 
	 */
	public void setK(int k) {
		this.k = k;
	}

	/**
     * @see #exposureOnlyScreen 
	 */
	public int getExpsoureOnlyScreen() {
		return exposureOnlyScreen;
	}

	/**
     * @see #exposureOnlyScreen 
	 */
	public void setExposureOnlyScreen(int exposureOnlyScreen) {
		this.exposureOnlyScreen = exposureOnlyScreen;
	}

	/**
     * @see #tempDirectory
	 */
	public String getTempDirectory() {
		return tempDirectory;
	}

	/**
     * @see #tempDirectory
	 */
	public void setTempDirectory(String tempDirectory) {
		this.tempDirectory = tempDirectory;
	}

	/**
     * @see #useOutcomeZeroCellCorrection
	 */
	public void setUseOutcomeZeroCellCorrection(int useZeroCellCorrection) {
		this.useOutcomeZeroCellCorrection = useZeroCellCorrection;
	}

	
	/**
     * @see #doFullOutput
	 */
	public int getDoFullOutput() {
		return doFullOutput;
	}

	/**
     * @see #doSparseOutput
	 */
	public int getDoSparseOutput() {
		return doSparseOutput;
	}

	/**
     * @see #doFullOutput
	 */
	public void setDoFullOutput(int doFullOutput) {
		this.doFullOutput = doFullOutput;
	}

	/**
     * @see #doSparseOutput
	 */
	public void setDoSparseOutput(int doSparseOutput) {
		this.doSparseOutput = doSparseOutput;
	}
	
	public int getNumDimensions() {
		return numDimensions;
	}
	
	public void setMode(String mode)
	throws Exception 
	{
		this.mode = mode;
		if (this.mode.equalsIgnoreCase(Hdps.hdpsModeLocal)) {
			this.hdpsController = new HdpsLocalController(this);
		} else if (this.mode.equalsIgnoreCase(Hdps.hdpsModeDB)) {
			this.hdpsController = new HdpsDbController(this);
		} else {
			throw new HdpsException(String.format(
					"Invalid mode %s specified.  Mode must be either LOCAL or DB.", mode));
		}
	}

	public String getVaribleRankingMethod() {
		return variableRankingMethod;
	}

	public void setVaribleRankingMethod(String varibleRankingMethod) {
		this.variableRankingMethod = varibleRankingMethod;
	}

	public int getInferServiceIntensityVars() {
		return inferServiceIntensityVars;
	}

	public void setInferServiceIntensityVars(int inferServiceIntensityVars) {
		this.inferServiceIntensityVars = inferServiceIntensityVars;
	}

	public int getExposureOnlyScreen() {
		return exposureOnlyScreen;
	}

	public int getUseOutcomeZeroCellCorrection() {
		return useOutcomeZeroCellCorrection;
	}

	/**
	 * @return the outcomeType
	 */
	public String getOutcomeType() {
		return outcomeType;
	}

	/**
	 * @param outcomeType the outcomeType to set
	 */
	public void setOutcomeType(String outcomeType) {
		this.outcomeType = outcomeType;
	}

	/**
	 * @return the fullOutputFilename
	 */
	public String getFullOutputFilename() {
		return fullOutputFilename;
	}

	/**
	 * @param fullOutputFilename the fullOutputFilename to set
	 */
	public void setFullOutputFilename(String fullOutputFilename) {
		this.fullOutputFilename = fullOutputFilename;
	}

	/**
	 * @return the sparseOutputFilename
	 */
	public String getSparseOutputFilename() {
		return sparseOutputFilename;
	}

	/**
	 * @param sparseOutputFilename the sparseOutputFilename to set
	 */
	public void setSparseOutputFilename(String sparseOutputFilename) {
		this.sparseOutputFilename = sparseOutputFilename;
	}

	/**
	 * @return the dbDriverClass
	 */
	public String getDbDriverClass() {
		return dbDriverClass;
	}

	/**
	 * @param dbDriverClass the dbDriverClass to set
	 */
	public void setDbDriverClass(String dbDriverClass) {
		this.dbDriverClass = dbDriverClass;
	}

	/**
	 * @return the dbUrl
	 */
	public String getDbUrl() {
		return dbUrl;
	}

	/**
	 * @param dbUrl the dbUrl to set
	 */
	public void setDbUrl(String dbUrl) {
		this.dbUrl = dbUrl;
	}

	/**
	 * @return the dbUsername
	 */
	public String getDbUsername() {
		return dbUsername;
	}

	/**
	 * @param dbUsername the dbUsername to set
	 */
	public void setDbUsername(String dbUsername) {
		this.dbUsername = dbUsername;
	}

	/**
	 * @return the dbPassword
	 */
	public String getDbPassword() {
		return dbPassword;
	}

	/**
	 * @param dbPassword the dbPassword to set
	 */
	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}
	
	/**
	 * @return the database's patient table name, if one was created
	 */
	public String getDbPatientTableName() {
		if ((this.hdpsController != null) &&
			(this.hdpsController.getClass() ==  HdpsDbController.class)) {
			
			HdpsDbController d = (HdpsDbController) this.hdpsController;
			return d.getPatientTableName();
		}
		return null;
	}
	
	/**
	 * @return the database's variable table name, if one was created
	 */
	public String getDbVarTableName() {
		if ((this.hdpsController != null) &&
			(this.hdpsController.getClass() ==  HdpsDbController.class)) {
			
			HdpsDbController d = (HdpsDbController) this.hdpsController;
			return d.getVarTableName();
		}
		return null;
	}
	
	/**
	 * @return the database's patient/variable table name, if one was created
	 */
	public String getDbPatientVarTableName() {
		if ((this.hdpsController != null) &&
			(this.hdpsController.getClass() ==  HdpsDbController.class)) {
			
			HdpsDbController d = (HdpsDbController) this.hdpsController;
			return d.getPatientVarTableName();
		}
		return null;
	}
}

